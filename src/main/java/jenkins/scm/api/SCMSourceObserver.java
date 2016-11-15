/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.scm.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jenkins.scm.impl.NoOpProjectObserver;

/**
 * Callback used by {@link SCMNavigator}.
 *
 * @since 0.3-beta-1
 */
public abstract class SCMSourceObserver {

    /**
     * Indicates who is asking for sources.
     *
     * @return a contextual item, typically a {@code OrganizationFolder}
     */
    @NonNull
    public abstract SCMSourceOwner getContext();

    /**
     * Provides a way of reporting progress.
     *
     * @return a logger
     */
    @NonNull
    public abstract TaskListener getListener();

    /**
     * Returns the subset of project names that this observer is interested in or {@code null} if
     * interested in all project names.
     * <p>
     * <strong>Implementations should not assume that the {@link #getIncludes()} will be honoured.</strong>
     * This method is designed to provide a <i>hint</i> to {@link SCMNavigator} implementations.
     *
     * @return the subset of project names that this observer is interested in or {@code null}.
     * @since FIXME
     */
    @CheckForNull
    public Set<String> getIncludes() {
        return null;
    }

    /**
     * Declare that a new “project” such as a source repository has been found.
     *
     * @param projectName a name of the project, such as a repository name within an organization; may be used as an
     * {@link Item#getName}
     * @return a secondary callback to customize the project, on which you must call {@link ProjectObserver#complete}
     * @throws IllegalArgumentException if this {@code projectName} has already been encountered
     */
    @NonNull
    public abstract ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException;

    /**
     * Adds extra metadata about the overall organization.
     * Currently no metadata keys are defined; placeholder for description, icon, URL, etc.
     *
     * @param key   a predefined attribute name
     * @param value some value, of a type defined by the attribute, perhaps null if allowed by the attribute
     *              documentation
     * @throws IllegalArgumentException if the attribute name is unrecognized, or this attribute was already added
     * @throws ClassCastException       if the attribute value is inappropriate for its type
     */
    public abstract void addAttribute(@NonNull String key, @Nullable Object value) throws IllegalArgumentException,
            ClassCastException;

    /**
     * Creates an observer that filters a delegates observer to the specified project names
     *
     * @param delegate     the delegate
     * @param projectNames the project names to watch out for.
     * @return an observer that wraps the supplied delegate.
     * @since FIXME
     */
    @NonNull
    public static <O extends SCMSourceObserver> SCMSourceObserver.Filter<O> filter(O delegate, String... projectNames) {
        return new SCMSourceObserver.Filter<O>(delegate, projectNames);
    }

    /**
     * Nested callback produced by {@link #observe}.
     */
    public static abstract class ProjectObserver {

        /**
         * Adds a source repository to be used from a new project.
         *
         * @param source a potential SCM source as in {@code MultiBranchProject.getSCMSources}; do not call
         * {@link SCMSource#setOwner} on it
         */
        public abstract void addSource(@NonNull SCMSource source);

        /**
         * Adds extra metadata about a specific project.
         * Currently no metadata keys are defined; placeholder for description, icon, URL, etc.
         *
         * @param key   a predefined attribute name
         * @param value some value, of a type defined by the attribute, perhaps null if allowed by the attribute
         *              documentation
         * @throws IllegalArgumentException if the attribute name is unrecognized, or this attribute was already added
         * @throws ClassCastException       if the attribute value is inappropriate for its type
         */
        public abstract void addAttribute(@NonNull String key, @Nullable Object value) throws
                IllegalArgumentException, ClassCastException;

        /**
         * To be called when finished defining one project.
         *
         * @throws IllegalStateException may be thrown if called twice
         * @throws InterruptedException  if processing of the final project was interrupted
         */
        public abstract void complete() throws IllegalStateException, InterruptedException;

    }

    public static abstract class Wrapped<O extends SCMSourceObserver> extends SCMSourceObserver {

        private final O delegate;

        protected Wrapped(O delegate) {
            this.delegate = delegate;
        }

        @Override
        @NonNull
        public SCMSourceOwner getContext() {
            return delegate.getContext();
        }

        @Override
        @NonNull
        public TaskListener getListener() {
            return delegate.getListener();
        }

        @Override
        @CheckForNull
        public Set<String> getIncludes() {
            return delegate.getIncludes();
        }

        @Override
        @NonNull
        public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
            return delegate.observe(projectName);
        }

        @Override
        public void addAttribute(@NonNull String key, @Nullable Object value)
                throws IllegalArgumentException, ClassCastException {
            delegate.addAttribute(key, value);
        }
    }

    public static class Filter<O extends SCMSourceObserver> extends Wrapped<O> {

        private final Set<String> projectNames;
        private final Set<String> remaining;


        public Filter(O delegate, String... projectNames) {
            super(delegate);
            this.projectNames = new HashSet<String>(Arrays.asList(projectNames));
            this.remaining = new HashSet<String>(this.projectNames);
        }

        @Override
        public Set<String> getIncludes() {
            return projectNames;
        }

        @NonNull
        @Override
        public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
            if (remaining.remove(projectName)) {
                return super.observe(projectName);
            } else {
                return NoOpProjectObserver.instance();
            }
        }
    }

}
