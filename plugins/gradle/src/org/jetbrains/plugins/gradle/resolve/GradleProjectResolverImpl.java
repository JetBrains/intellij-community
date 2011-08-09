package org.jetbrains.plugins.gradle.resolve;

import com.intellij.execution.rmi.RemoteObject;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.OfflineIdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.rmi.RemoteException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolverImpl extends RemoteObject implements GradleProjectResolver, RemoteGradleService {

  private final BlockingQueue<ProjectConnection>             myConnections = new LinkedBlockingQueue<ProjectConnection>();
  private final AtomicReference<RemoteGradleProcessSettings> mySettings    = new AtomicReference<RemoteGradleProcessSettings>();
  
  @NotNull
  @Override
  public GradleProject resolveProjectInfo(@NotNull String projectPath) throws RemoteException {
    try {
      return doResolve(projectPath);
    }
    catch (Throwable e) {
      throw new IllegalStateException(GradleBundle.message("gradle.import.text.error.resolve.generic", projectPath), e);
    }
  }

  @NotNull
  private GradleProject doResolve(@NotNull String projectPath) {
    ProjectConnection connection = getConnection(projectPath);
    OfflineIdeaProject project = connection.getModel(OfflineIdeaProject.class);
    GradleProjectImpl result = new GradleProjectImpl();
    result.setJdk(project.getJdkName());
    result.setLanguageLevel(project.getLanguageLevel().getLevel());
    // TODO den build modules here
    return result;
  }

  /**
   * Allows to retrieve gradle api connection to use for the given project.
   * 
   * @param projectPath     target project path
   * @return                connection to use
   * @throws IllegalStateException    if it's not possible to create the connection
   */
  @NotNull
  private ProjectConnection getConnection(@NotNull String projectPath) throws IllegalStateException {
    File projectFile = new File(projectPath);
    if (!projectFile.isFile()) {
      throw new IllegalArgumentException(GradleBundle.message("gradle.import.text.error.invalid.path", projectPath));
    }
    File projectDir = projectFile.getParentFile();
    GradleConnector connector = GradleConnector.newConnector();
    RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {
      connector.useInstallation(new File(settings.getGradleHome()));
    } 
    connector.forProjectDirectory(projectDir);
    ProjectConnection connection = connector.connect();
    if (connection == null) {
      throw new IllegalStateException(String.format(
        "Can't create connection to the target project via gradle tooling api. Project path: '%s'", projectPath
      ));
    }
    myConnections.add(connection);
    return connection;
  }
  
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void unreferenced() {
    releaseConnectionIfPossible();
    super.unreferenced();
  }

  private void releaseConnectionIfPossible() {
    while (!myConnections.isEmpty()) {
      try {
        ProjectConnection connection = myConnections.poll(1, TimeUnit.SECONDS);
        connection.close();
      }
      catch (InterruptedException e) {
        GradleLog.LOG.warn("Detected unexpected thread interruption on releasing gradle connections", e);
        Thread.currentThread().interrupt();
      }
      catch (Throwable e) {
        GradleLog.LOG.warn("Got unexpected exception on closing project connection created by the gradle tooling api", e);
      }
    }
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) {
    mySettings.set(settings); 
  }
}
