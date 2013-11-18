package com.intellij.remoteServer.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.remoteServer.agent.RemoteAgentManager;
import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudAgentLogger;
import com.intellij.remoteServer.agent.util.CloudGitAgent;
import com.intellij.remoteServer.agent.util.DeploymentData;
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

public abstract class CloudGitServerRuntimeInstanceBase<
  DC extends CloudDeploymentNameConfiguration,
  AC extends CloudAgentConfigBase,
  A extends CloudGitAgent<AC, ?>,
  SC extends AC,
  DR extends CloudGitDeploymentRuntime>
  extends CloudServerRuntimeInstance<DC>
  implements CloudDeploymentNameProvider {

  private static final Logger LOG = Logger.getInstance("#" + CloudGitServerRuntimeInstanceBase.class.getName());

  private final A myAgent;
  private final AgentTaskExecutor myAgentTaskExecutor;

  private final SC myConfiguration;
  private final ServerTaskExecutor myTasksExecutor;

  public CloudGitServerRuntimeInstanceBase(SC configuration,
                                           ServerTaskExecutor tasksExecutor,
                                           List<File> libraries,
                                           List<Class<?>> commonJarClasses,
                                           String specificsModuleName,
                                           String specificJarPath,
                                           Class<A> agentInterface,
                                           String agentClassName) throws Exception {
    myConfiguration = configuration;
    myTasksExecutor = tasksExecutor;

    RemoteAgentManager agentManager = RemoteAgentManager.getInstance();
    myAgent = agentManager.createAgent(agentManager.createReflectiveThreadProxyFactory(getClass().getClassLoader()),
                                       libraries,
                                       commonJarClasses,
                                       specificsModuleName,
                                       specificJarPath,
                                       agentInterface,
                                       agentClassName,
                                       getClass());
    myAgentTaskExecutor = new AgentTaskExecutor();
  }

  @NotNull
  @Override
  public String getDeploymentName(@NotNull DeploymentSource source) {
    return CloudDeploymentNameProvider.DEFAULT_NAME_PROVIDER.getDeploymentName(source);
  }

  public void connect(final ServerConnector.ConnectionCallback<DC> callback) {
    myAgentTaskExecutor.execute(new Computable() {

                                  @Override
                                  public Object compute() {
                                    doConnect(myConfiguration,
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
                                    callback.connected(CloudGitServerRuntimeInstanceBase.this);
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
    myTasksExecutor.submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        createDeploymentRuntime(myConfiguration, myTasksExecutor, task, logManager).deploy(callback);
      }
    }, callback);
  }

  @Override
  public void computeDeployments(@NotNull final ServerRuntimeInstance.ComputeDeploymentsCallback callback) {
    myTasksExecutor.submit(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myAgentTaskExecutor.execute(new Computable<DeploymentData[]>() {

                                      @Override
                                      public DeploymentData[] compute() {
                                        return myAgent.getDeployments();
                                      }
                                    },
                                    new CallbackWrapper<DeploymentData[]>() {

                                      @Override
                                      public void onSuccess(DeploymentData[] deployments) {
                                        for (DeploymentData deployment : deployments) {
                                          callback.addDeployment(deployment.getName());
                                        }
                                        callback.succeeded();
                                      }

                                      @Override
                                      public void onError(String message) {
                                        callback.errorOccurred(message);
                                      }
                                    }
        );
      }
    }, callback);
  }

  @Override
  public void disconnect() {
    myTasksExecutor.submit(new Runnable() {

      @Override
      public void run() {
        myAgent.disconnect();
      }
    });
  }

  @Override
  public ServerTaskExecutor getTaskExecutor() {
    return myTasksExecutor;
  }

  protected final A getAgent() {
    return myAgent;
  }

  protected final AgentTaskExecutor getAgentTaskExecutor() {
    return myAgentTaskExecutor;
  }

  public DR createDeploymentRuntime(final DeployToServerRunConfiguration<?, DC> runConfiguration)
    throws ServerRuntimeException {
    return createDeploymentRuntime(myConfiguration,
                                   myTasksExecutor,
                                   new DeploymentTask<DC>() {

                                     @NotNull
                                     @Override
                                     public DeploymentSource getSource() {
                                       return runConfiguration.getDeploymentSource();
                                     }

                                     @NotNull
                                     @Override
                                     public DC getConfiguration() {
                                       return runConfiguration.getDeploymentConfiguration();
                                     }

                                     @NotNull
                                     @Override
                                     public Project getProject() {
                                       return runConfiguration.getProject();
                                     }

                                     @Override
                                     public boolean isDebugMode() {
                                       return false;
                                     }
                                   },
                                   null
    );
  }

  protected abstract DR createDeploymentRuntime(SC configuration,
                                                ServerTaskExecutor serverTaskExecutor,
                                                DeploymentTask<DC> deploymentTask,
                                                @Nullable DeploymentLogManager logManager) throws ServerRuntimeException;

  protected abstract void doConnect(SC configuration, CloudAgentLogger logger);
}
