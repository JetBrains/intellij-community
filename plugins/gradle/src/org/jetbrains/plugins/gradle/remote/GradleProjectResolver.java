package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleProject;
import org.jetbrains.plugins.gradle.notification.GradleTaskId;

import java.rmi.RemoteException;

/**
 * Defines common interface for resolving gradle project, i.e. building object-level representation of <code>'build.gradle'</code>.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 10:58 AM
 */
public interface GradleProjectResolver extends RemoteGradleService {

  /**
   * Builds object-level representation of the gradle project file contained at the target directory (dependencies are not resolved
   * during that).
   * <p/>
   * <b>Note:</b> gradle api doesn't allow to explicitly define project file to use though it's possible to do that via
   * command-line switch. So, we want to treat the argument as a target project file name when that is supported.
   * 
   * @param id                id of the current 'resolve project info' task
   * @param projectPath       absolute path to the gradle project file
   * @param downloadLibraries flag that specifies if third-party libraries that are not available locally should be resolved (downloaded)
   * @return                  object-level representation of the target gradle project
   * @throws RemoteException            in case of unexpected exception during remote communications
   * @throws GradleApiException      in case of unexpected exception thrown from Gradle API
   * @throws IllegalArgumentException   if given path doesn't point to directory that contains gradle project or if gradle api
   *                                    returns invalid data
   * @throws IllegalStateException      if it's not possible to resolve target project info
   */
  @NotNull
  GradleProject resolveProjectInfo(@NotNull GradleTaskId id, @NotNull String projectPath, boolean downloadLibraries)
    throws RemoteException, GradleApiException, IllegalArgumentException, IllegalStateException;
}
