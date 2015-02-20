package com.intellij.remoteServer.util;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudAgentLogger;
import com.intellij.remoteServer.agent.util.CloudGitAgent;
import com.intellij.remoteServer.agent.util.CloudRemoteApplication;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudMultiSourceServerRuntimeInstance<
  DC extends CloudDeploymentNameConfiguration,
  AC extends CloudAgentConfigBase,
  A extends CloudGitAgent<AC, ?>,
  SC extends AC>
  extends CloudServerRuntimeInstance<DC, A, SC> {

  private static final Logger LOG = Logger.getInstance("#" + CloudMultiSourceServerRuntimeInstance.class.getName());

  private final ServerType<?> myServerType;

  public CloudMultiSourceServerRuntimeInstance(ServerType<?> serverType,
                                               SC configuration,
                                               ServerTaskExecutor tasksExecutor,
                                               List<File> libraries,
                                               List<Class<?>> commonJarClasses,
                                               String specificsModuleName,
                                               String specificJarPath,
                                               Class<A> agentInterface,
                                               String agentClassName) throws Exception {
    super(configuration,
          tasksExecutor,
          libraries,
          commonJarClasses,
          specificsModuleName,
          specificJarPath,
          agentInterface,
          agentClassName);

    myServerType = serverType;
  }

  @Override
  public A getAgent() {
    return super.getAgent();
  }

  @NotNull
  @Override
  public String getDeploymentName(@NotNull DeploymentSource source) {
    return CloudDeploymentNameProvider.DEFAULT_NAME_PROVIDER.getDeploymentName(source);
  }

  public void connect(final ServerConnector.ConnectionCallback<DC> callback) {
    getAgentTaskExecutor().execute(new Computable() {

                                     @Override
                                     public Object compute() {
                                       doConnect(getConfiguration(),
                                                 new CloudAgentLogger() {

                                                   @Override
                                                   public void debugEx(Exception e) {
                                                     LOG.debug(e);
                                                   }

                                                   @Override
                                                   public void debug(String message) {
                                                     LOG.debug(message);
                                                   }
                                                 });
                                       return null;
                                     }
                                   }, new CallbackWrapper() {

                                     @Override
                                     public void onSuccess(Object result) {
                                       callback.connected(CloudMultiSourceServerRuntimeInstance.this);
                                     }

                                     @Override
                                     public void onError(String message) {
                                       callback.errorOccurred(message);
                                     }
                                   }
    );
  }

  @Override
  public void deploy(@NotNull final DeploymentTask<DC> task,
                     @NotNull final DeploymentLogManager logManager,
                     @NotNull final ServerRuntimeInstance.DeploymentOperationCallback callback) {
    getTaskExecutor().submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        createDeploymentRuntime(task, logManager).deploy(callback);
      }
    }, callback);
  }

  @Override
  public void disconnect() {
    getTaskExecutor().submit(new Runnable() {

      @Override
      public void run() {
        getAgent().disconnect();
      }
    });
  }

  public CloudDeploymentRuntime createDeploymentRuntime(final DeployToServerRunConfiguration<?, DC> runConfiguration)
    throws ServerRuntimeException {
    return createDeploymentRuntime(runConfiguration.getDeploymentSource(),
                                   runConfiguration.getDeploymentConfiguration(),
                                   runConfiguration.getProject());
  }

  public CloudDeploymentRuntime createDeploymentRuntime(final DeploymentSource source, final DC configuration, final Project project)
    throws ServerRuntimeException {
    return createDeploymentRuntime(new DeploymentTask<DC>() {

      @NotNull
      @Override
      public DeploymentSource getSource() {
        return source;
      }

      @NotNull
      @Override
      public DC getConfiguration() {
        return configuration;
      }

      @NotNull
      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public boolean isDebugMode() {
        return false;
      }

      @NotNull
      @Override
      public ExecutionEnvironment getExecutionEnvironment() {
        throw new UnsupportedOperationException();
      }
    }, null);
  }

  private CloudDeploymentRuntime createDeploymentRuntime(DeploymentTask<DC> deploymentTask,
                                                         @Nullable DeploymentLogManager logManager) throws ServerRuntimeException {
    DeploymentSource source = deploymentTask.getSource();
    for (CloudDeploymentRuntimeProvider provider : CloudDeploymentConfiguratorBase.getDeploymentRuntimeProviders(myServerType)) {
      CloudDeploymentRuntime result = provider.createDeploymentRuntime(source, this, deploymentTask, logManager);
      if (result != null) {
        return result;
      }
    }
    throw new ServerRuntimeException("Unknown deployment source");
  }

  @Override
  protected CloudApplicationRuntime createApplicationRuntime(CloudRemoteApplication application) {
    return new CloudGitApplicationRuntime(this, application.getName(), null);
  }

  protected abstract void doConnect(SC configuration, CloudAgentLogger logger);

  public ServerType<?> getCloudType() {
    return myServerType;
  }
}
