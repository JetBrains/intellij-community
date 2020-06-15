// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle;

import static com.intellij.util.ReflectionUtil.getDeclaredField;
import static com.intellij.util.ReflectionUtil.getField;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.project.DistributionFactoryExt;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.tooling.loader.rt.MarkerRt;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@ApiStatus.Internal
@Service
public class GradleConnectorService implements Disposable {
  private static final Logger LOG = Logger.getInstance(GradleConnectorService.class);

  /** disable stop IDLE Gradle daemons on IDE project close. Applicable for Gradle versions w/o disconnect support (older than 6.5). */
  private static final boolean DISABLE_STOP_OLD_IDLE_DAEMONS = Boolean.getBoolean("idea.gradle.disableStopIdleDaemonsOnProjectClose");

  private static final Set<String> REPORTED_JAVA11_ISSUE = ContainerUtil.newConcurrentSet();

  private final ConcurrentHashMap<String, GradleProjectConnection> connectorsMap = new ConcurrentHashMap<>();

  @Override
  public void dispose() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    disconnectGradleConnections();
    stopIdleDaemonsOfOldVersions();
  }

  private void stopIdleDaemonsOfOldVersions() {
    if (DISABLE_STOP_OLD_IDLE_DAEMONS) return;
    try {
      if (ProjectUtil.getOpenProjects().length == 0) {
        GradleVersion gradleVersion_6_5 = GradleVersion.version("6.5");
        List<DaemonState> idleDaemons = ContainerUtil.filter(
          GradleDaemonServices.getDaemonsStatus(), daemonState ->
            daemonState.getStatus().toLowerCase() == "idle" &&
            GradleVersion.version(daemonState.getVersion()).compareTo(gradleVersion_6_5) < 0);

        if (!idleDaemons.isEmpty()) {
          GradleDaemonServices.stopDaemons(idleDaemons);
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Failed to stop Gradle daemons during project close", e);
    }
  }

  private void disconnectGradleConnections() {
    connectorsMap.values().forEach(GradleProjectConnection::disconnect);
    connectorsMap.clear();
  }

  private ProjectConnection getConnection(
    ConnectorParams connectorParams,
    ExternalSystemTaskId taskId,
    ExternalSystemTaskNotificationListener listener,
    CancellationToken cancellationToken) {
    return connectorsMap.compute(connectorParams.projectPath, (key, conn) -> {
      if (connectorParams == conn.params) {
        return conn;
      }
      GradleConnector newConnector = createConnector(connectorParams);
      ProjectConnection newConnection = newConnector.connect();
      if (newConnection == null) {
        throw new IllegalStateException(
          "Can't create connection to the target project via gradle tooling api. Project path: '${connectorParams.projectPath}'");
      }
      workaroundJavaVersionIssueIfNeeded(newConnection, taskId, listener, cancellationToken);

      if (conn != null && connectorParams != conn.params) {
        // close obsolete connection, can not disconnect the connector here - it may cause build cancel for the new connection operations
        WrappedConnection unwrappedConnection = (WrappedConnection)conn.connection;
        unwrappedConnection.delegate.close();
      }
      WrappedConnection wrappedConnection = new WrappedConnection(newConnection);
      return new GradleProjectConnection(connectorParams, newConnector, wrappedConnection);
    }).connection;
  }

  private static class GradleProjectConnection {
    final ConnectorParams params;
    final GradleConnector connector;
    private ProjectConnection connection;

    GradleProjectConnection(ConnectorParams params, GradleConnector connector, ProjectConnection connection) {
      this.params = params;
      this.connector = connector;
      this.connection = connection;
    }

    void disconnect() {
      try {
        connector.disconnect();
      }
      catch (Exception e) {
        LOG.warn("Failed to disconnect Gradle connector during project close. Project path: '${params.projectPath}'", e);
      }
    }
  }

  private static class WrappedConnection implements ProjectConnection {
    private final ProjectConnection delegate;

    WrappedConnection(ProjectConnection delegate) {
      this.delegate = delegate;
    }

    @Override
    public <T> T getModel(Class<T> aClass) throws GradleConnectionException, IllegalStateException {
      return delegate.getModel(aClass);
    }

    @Override
    public <T> void getModel(Class<T> aClass, ResultHandler<? super T> handler) throws IllegalStateException {
      delegate.getModel(aClass, handler);
    }

    @Override
    public BuildLauncher newBuild() {
      return delegate.newBuild();
    }

    @Override
    public TestLauncher newTestLauncher() {
      return delegate.newTestLauncher();
    }

    @Override
    public <T> ModelBuilder<T> model(Class<T> aClass) {
      return delegate.model(aClass);
    }

    @Override
    public <T> BuildActionExecuter<T> action(BuildAction<T> action) {
      return delegate.action(action);
    }

    @Override
    public BuildActionExecuter.Builder action() {
      return delegate.action();
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<Path> list) {
      delegate.notifyDaemonsAboutChangedPaths(list);
    }

    @Override
    public void close() {
      throw new IllegalStateException("This connection should not be closed explicitly.");
    }
  }

  private static final class ConnectorParams {
    String projectPath;
    String serviceDirectory;
    DistributionType distributionType;
    String gradleHome;
    String wrapperPropertyFile;
    Boolean verboseProcessing;
    Integer ttlMs;
  }

  private static GradleConnectorService getInstance(String projectPath, ExternalSystemTaskId taskId) {
    Project project = (taskId == null) ? null : taskId.findProject();
    if (project == null) {
      for (Project openProject : ProjectUtil.getOpenProjects()) {
        String projectBasePath = openProject.getBasePath();
        if (projectBasePath == null) continue;
        if (FileUtil.isAncestor(projectBasePath, projectPath, false)) {
          project = openProject;
          break;
        }
      }
    }
    return (project == null) ? null : project.getService(GradleConnectorService.class);
  }

  public static <R> R withGradleConnection(
    String projectPath,
    ExternalSystemTaskId taskId,
    GradleExecutionSettings executionSettings,
    ExternalSystemTaskNotificationListener listener,
    CancellationToken cancellationToken,
    Function<ProjectConnection, R> function) {
    ConnectorParams connectionParams = new ConnectorParams();
    connectionParams.projectPath = projectPath;
    connectionParams.serviceDirectory = (executionSettings == null) ? null : executionSettings.getServiceDirectory();
    connectionParams.distributionType = (executionSettings == null) ? null : executionSettings.getDistributionType();
    connectionParams.gradleHome = (executionSettings == null) ? null : executionSettings.getGradleHome();
    connectionParams.wrapperPropertyFile = (executionSettings == null) ? null : executionSettings.getWrapperPropertyFile();
    connectionParams.verboseProcessing = (executionSettings == null) ? null : executionSettings.isVerboseProcessing();
    connectionParams.ttlMs = (executionSettings == null) ? null : (int)executionSettings.getRemoteProcessIdleTtlInMs();
    GradleConnectorService connectionService = getInstance(projectPath, taskId);
    if (connectionService != null) {
      ProjectConnection connection = connectionService.getConnection(connectionParams, taskId, listener, cancellationToken);
      return function.apply(connection);
    }
    else {
      GradleConnector newConnector = createConnector(connectionParams);
      ProjectConnection connection = newConnector.connect();
      workaroundJavaVersionIssueIfNeeded(connection, taskId, listener, cancellationToken);
      return function.apply(connection);
    }
  }

  private static GradleConnector createConnector(ConnectorParams connectorParams) {
    GradleConnector connector = GradleConnector.newConnector();
    File projectDir = new File(connectorParams.projectPath);
    File gradleUserHome = (connectorParams.serviceDirectory == null) ? null : new File(connectorParams.serviceDirectory);

    if (connectorParams.distributionType == DistributionType.LOCAL) {
      File gradleHome = (connectorParams.gradleHome == null) ? null : new File(connectorParams.gradleHome);
      if (gradleHome != null) {
        connector.useInstallation(gradleHome);
      }
    }
    else if (connectorParams.distributionType == DistributionType.WRAPPED) {
      if (connectorParams.wrapperPropertyFile != null) {
        DistributionFactoryExt.setWrappedDistribution(connector, connectorParams.wrapperPropertyFile, gradleUserHome, projectDir);
      }
    }

    // Setup Grade user home if necessary
    if (gradleUserHome != null) {
      connector.useGradleUserHomeDir(gradleUserHome);
    }
    // Setup logging if necessary
    if (connectorParams.verboseProcessing && (connector instanceof DefaultGradleConnector)) {
      ((DefaultGradleConnector)connector).setVerboseLogging(true);
    }
    // do not spawn gradle daemons during test execution
    Application app = ApplicationManager.getApplication();
    int ttl = (app != null && app.isUnitTestMode()) ? 10000 :
              (connectorParams.ttlMs != null) ? connectorParams.ttlMs : -1;
    if (ttl > 0 && connector instanceof DefaultGradleConnector) {
      ((DefaultGradleConnector)connector).daemonMaxIdleTime(ttl, TimeUnit.MILLISECONDS);
    }

    connector.forProjectDirectory(projectDir);
    return connector;
  }

  // workaround for https://github.com/gradle/gradle/issues/8431
  // TODO should be removed when the issue will be fixed at the Gradle tooling api side
  private static void workaroundJavaVersionIssueIfNeeded(
    ProjectConnection connection,
    ExternalSystemTaskId taskId,
    ExternalSystemTaskNotificationListener listener,
    CancellationToken cancellationToken) {
    ProjectConnection unwrappedConnection = (connection instanceof WrappedConnection) ? ((WrappedConnection)connection).delegate : connection;
    String buildRoot = null;
    if (Registry.is("gradle.java11.issue.workaround", true)
        && taskId != null && listener != null && JavaVersion.current().feature > 8) {
      try {
        BuildEnvironment environment = GradleExecutionHelper.getBuildEnvironment(unwrappedConnection, taskId, listener, cancellationToken);
        if (environment != null) {
          try {
            buildRoot = environment.getBuildIdentifier().getRootDir().getPath();
          }
          catch (Exception ignored) {
          }
        }
        String gradleVersion = (environment == null) ? null : (environment.getGradle() == null) ? null : environment.getGradle().getGradleVersion();
        if (gradleVersion == null || GradleVersion.version(gradleVersion).getBaseVersion().compareTo(GradleVersion.version("4.7")) < 0) {
          Field conn = getField(unwrappedConnection.getClass(), unwrappedConnection, null, "connection");
          Field actionExecutor = getField(conn.getClass(), conn, null, "actionExecutor");
          Field actionExecutorDelegate = getField(actionExecutor.getClass(), actionExecutor, null, "delegate");
          Field delegateActionExecutor = getField(actionExecutorDelegate.getClass(), actionExecutorDelegate, null, "actionExecutor");
          Field delegateActionExecutorDelegate = getField(delegateActionExecutor.getClass(), delegateActionExecutor, null, "delegate");
          Field distributionField = getDeclaredField(delegateActionExecutorDelegate.getClass(), "distribution");
          distributionField.set(delegateActionExecutorDelegate,
                                  new DistributionWrapper((Distribution)distributionField.get(delegateActionExecutorDelegate)));
        }
      }
      catch (Throwable t) {
        String buildId = taskId.getIdeProjectId() + StringUtil.notNullize(buildRoot);
        if (REPORTED_JAVA11_ISSUE.add(buildId)) {
          LOG.error(t);
        }
        else {
          LOG.debug(t);
        }
      }
    }
  }

  /**
   * workaround for https://github.com/gradle/gradle/issues/8431
   * TODO should be removed when the issue will be fixed at the Gradle tooling api side
   */
  private static class DistributionWrapper implements Distribution {
    private final Distribution myDistribution;
    private final File myRtJarFile = new File(PathUtil.getCanonicalPath(PathManager.getJarPathForClass(MarkerRt.class)));

    DistributionWrapper(Distribution distribution) {
      myDistribution = distribution;
    }

    @Override
    public String getDisplayName() {
      return myDistribution.getDisplayName();
    }

    @Override
    public ClassPath getToolingImplementationClasspath(
      ProgressLoggerFactory factory, InternalBuildProgressListener listener, File file, BuildCancellationToken token) {
      ClassPath classpath = myDistribution.getToolingImplementationClasspath(factory, listener, file, token);
      return DefaultClassPath.of(myRtJarFile).plus(classpath);
    }
  }
}