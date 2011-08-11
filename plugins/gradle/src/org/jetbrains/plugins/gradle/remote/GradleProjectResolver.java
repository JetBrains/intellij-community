package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleProject;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Defines common interface for resolving gradle project, i.e. building object-level representation of <code>'build.gradle'</code>.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 10:58 AM
 */
public interface GradleProjectResolver extends Remote {

  /**
   * Builds object-level representation of the gradle project file contained at the target directory (dependencies are not resolved
   * during that).
   * <p/>
   * <b>Note:</b> gradle api doesn't allow to explicitly define project file to use though it's possible to do that via
   * command-line switch. So, we want to treat the argument as a target project file name when that is supported.
   * 
   * @param projectPath       absolute path to the gradle project file
   * @return                  object-level representation of the target gradle project
   * @throws RemoteException            in case of unexpected exception during remote communications
   * @throws IllegalArgumentException   if given path doesn't point to directory that contains gradle project
   * @throws IllegalStateException      if it's not possible to resolve target project info
   */
  @NotNull
  GradleProject resolveProjectInfo(@NotNull String projectPath) throws RemoteException, IllegalArgumentException, IllegalStateException;
}
