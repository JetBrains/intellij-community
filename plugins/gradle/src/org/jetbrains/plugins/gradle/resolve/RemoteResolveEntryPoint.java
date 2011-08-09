package org.jetbrains.plugins.gradle.resolve;

import com.intellij.execution.rmi.RemoteServer;

/**
 * Entry point to the processing that resolves gradle project via its tooling api and exposes the result to IntelliJ process.
 * <p/>
 * Rationale: we want to import gradle project, i.e. setup IDE structure (project, modules, libraries etc) on the basis
 * of existing <code>'build.gradle'</code> file. We need to have object-level representation of the
 * <code>'build.gradle'</code> file data then. That is done via gradle api, i.e. it should be initialised and asked to
 * perform the job. The thing is that we don't want to have that done at IJ process. The reason is that that is not our
 * codebase, so, we can't be sure that it doesn't leaks memory, consumes perm gen, calls <code>System.exit()</code> etc. 
 * 
 * @author Denis Zhdanov
 * @since 8/5/11 11:50 AM
 */
public class RemoteResolveEntryPoint extends RemoteServer {
  public static void main(String[] args) throws Exception {
    start(new GradleProjectResolverImpl());
  }
}
