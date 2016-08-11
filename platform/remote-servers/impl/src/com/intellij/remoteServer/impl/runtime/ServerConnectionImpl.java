/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentImpl;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.impl.runtime.deployment.LocalDeploymentImpl;
import com.intellij.remoteServer.impl.runtime.log.DeploymentLogManagerImpl;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerImpl;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.*;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnectionData;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnectionDataNotAvailableException;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnector;
import com.intellij.util.Consumer;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ServerConnectionImpl<D extends DeploymentConfiguration> implements ServerConnection<D> {
  private static final Logger LOG = Logger.getInstance(ServerConnectionImpl.class);
  private final RemoteServer<?> myServer;
  private final ServerConnector<D> myConnector;
  private final ServerConnectionEventDispatcher myEventDispatcher;
  private final ServerConnectionManagerImpl myConnectionManager;
  private volatile ConnectionStatus myStatus = ConnectionStatus.DISCONNECTED;
  private volatile String myStatusText;
  private volatile ServerRuntimeInstance<D> myRuntimeInstance;
  private final Map<String, DeploymentImpl> myRemoteDeployments = new HashMap<>();
  private final Map<String, LocalDeploymentImpl> myLocalDeployments = new HashMap<>();
  private final Map<String, DeploymentLogManagerImpl> myLogManagers = ContainerUtil.newConcurrentMap();

  public ServerConnectionImpl(RemoteServer<?> server,
                              ServerConnector connector,
                              @Nullable ServerConnectionManagerImpl connectionManager,
                              ServerConnectionEventDispatcher eventDispatcher) {
    myServer = server;
    myConnector = connector;
    myConnectionManager = connectionManager;
    myEventDispatcher = eventDispatcher;
  }

  @NotNull
  @Override
  public RemoteServer<?> getServer() {
    return myServer;
  }

  @NotNull
  @Override
  public ConnectionStatus getStatus() {
    return myStatus;
  }

  @NotNull
  @Override
  public String getStatusText() {
    return myStatusText != null ? myStatusText : myStatus.getPresentableText();
  }

  @Override
  public void connect(@NotNull final Runnable onFinished) {
    doDisconnect();
    connectIfNeeded(new ServerConnector.ConnectionCallback<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> serverRuntimeInstance) {
        onFinished.run();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        onFinished.run();
      }
    });
  }

  @Override
  public void disconnect() {
    if (myConnectionManager != null) {
      myConnectionManager.removeConnection(myServer);
    }
    doDisconnect();
  }

  private void doDisconnect() {
    if (myStatus == ConnectionStatus.CONNECTED) {
      if (myRuntimeInstance != null) {
        myRuntimeInstance.disconnect();
        myRuntimeInstance = null;
      }
      setStatus(ConnectionStatus.DISCONNECTED);
      for (DeploymentLogManagerImpl logManager : myLogManagers.values()) {
        logManager.disposeLogs();
      }
    }
  }

  @Override
  public void deploy(@NotNull final DeploymentTask<D> task, @NotNull final ParameterizedRunnable<String> onDeploymentStarted) {
    connectIfNeeded(new ConnectionCallbackBase<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        LocalDeploymentImpl deployment = new LocalDeploymentImpl(instance,
                                                                 ServerConnectionImpl.this,
                                                                 DeploymentStatus.DEPLOYING,
                                                                 null,
                                                                 null,
                                                                 task);
        String deploymentName = deployment.getName();
        synchronized (myLocalDeployments) {
          myLocalDeployments.put(deploymentName, deployment);
        }
        DeploymentLogManagerImpl logManager = new DeploymentLogManagerImpl(task.getProject(), new ChangeListener())
          .withMainHandlerVisible(true);
        LoggingHandlerImpl handler = logManager.getMainLoggingHandler();
        myLogManagers.put(deploymentName, logManager);
        handler.printlnSystemMessage("Deploying '" + deploymentName + "'...");
        onDeploymentStarted.run(deploymentName);
        instance
          .deploy(task, logManager, new DeploymentOperationCallbackImpl(deploymentName, (DeploymentTaskImpl<D>)task, handler, deployment));
      }
    });
  }

  @Nullable
  @Override
  public DeploymentLogManager getLogManager(@NotNull Deployment deployment) {
    return myLogManagers.get(deployment.getName());
  }

  @NotNull
  public DeploymentLogManager getOrCreateLogManager(@NotNull Project project, @NotNull Deployment deployment) {
    DeploymentLogManagerImpl result = (DeploymentLogManagerImpl)getLogManager(deployment);
    if (result == null) {
      result = new DeploymentLogManagerImpl(project, new ChangeListener());
      myLogManagers.put(deployment.getName(), result);
    }
    return result;
  }

  @Override
  public void computeDeployments(@NotNull final Runnable onFinished) {
    connectIfNeeded(new ConnectionCallbackBase<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        computeDeployments(instance, onFinished);
      }
    });
  }

  private void computeDeployments(ServerRuntimeInstance<D> instance, final Runnable onFinished) {
    instance.computeDeployments(new ServerRuntimeInstance.ComputeDeploymentsCallback() {
      private final List<DeploymentImpl> myDeployments = new ArrayList<>();

      @Override
      public void addDeployment(@NotNull String deploymentName) {
        addDeployment(deploymentName, null);
      }

      @Override
      public void addDeployment(@NotNull String deploymentName, @Nullable DeploymentRuntime deploymentRuntime) {
        addDeployment(deploymentName, deploymentRuntime, null, null);
      }

      @Override
      public Deployment addDeployment(@NotNull String deploymentName,
                                      @Nullable DeploymentRuntime deploymentRuntime,
                                      @Nullable DeploymentStatus deploymentStatus,
                                      @Nullable String deploymentStatusText) {
        DeploymentImpl result;
        if (deploymentStatus == null) {
          deploymentStatus = DeploymentStatus.DEPLOYED;
        }
        synchronized (myRemoteDeployments) {
          result = myRemoteDeployments.get(deploymentName);
          if (result == null) {
            result = new DeploymentImpl(ServerConnectionImpl.this,
                                        deploymentName,
                                        deploymentStatus,
                                        deploymentStatusText,
                                        deploymentRuntime,
                                        null);
          }
          else if (!result.getStatus().isTransition()) {
            result.changeState(result.getStatus(), deploymentStatus, deploymentStatusText, deploymentRuntime);
          }
          myDeployments.add(result);
        }
        return result;
      }

      @Override
      public void succeeded() {
        synchronized (myRemoteDeployments) {
          myRemoteDeployments.clear();
          for (DeploymentImpl deployment : myDeployments) {
            myRemoteDeployments.put(deployment.getName(), deployment);
          }
        }
        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
        onFinished.run();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        synchronized (myRemoteDeployments) {
          myRemoteDeployments.clear();
        }
        myStatusText = "Cannot obtain deployments: " + errorMessage;
        myEventDispatcher.queueConnectionStatusChanged(ServerConnectionImpl.this);
        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
        onFinished.run();
      }
    });
  }

  @Override
  public void undeploy(@NotNull Deployment deployment, @NotNull final DeploymentRuntime runtime) {
    final String deploymentName = deployment.getName();
    final DeploymentImpl deploymentImpl;
    final Map<String, ? extends DeploymentImpl> deploymentsMap;
    synchronized (myLocalDeployments) {
      synchronized (myRemoteDeployments) {
        DeploymentImpl localDeployment = myLocalDeployments.get(deploymentName);
        if (localDeployment != null) {
          deploymentImpl = localDeployment;
          deploymentsMap = myLocalDeployments;
        }
        else {
          DeploymentImpl remoteDeployment = myRemoteDeployments.get(deploymentName);
          if (remoteDeployment != null) {
            deploymentImpl = remoteDeployment;
            deploymentsMap = myRemoteDeployments;
          }
          else {
            deploymentImpl = null;
            deploymentsMap = null;
          }
        }
        if (deploymentImpl != null) {
          deploymentImpl.changeState(DeploymentStatus.DEPLOYED, DeploymentStatus.UNDEPLOYING, null, null);
        }
      }
    }

    myEventDispatcher.queueDeploymentsChanged(this);
    DeploymentLogManagerImpl logManager = myLogManagers.get(deploymentName);
    final LoggingHandlerImpl loggingHandler = logManager == null ? null : logManager.getMainLoggingHandler();
    final Consumer<String> logConsumer = message -> {
      if (loggingHandler == null) {
        LOG.info(message);
      }
      else {
        loggingHandler.printlnSystemMessage(message);
      }
    };

    logConsumer.consume("Undeploying '" + deploymentName + "'...");
    runtime.undeploy(new DeploymentRuntime.UndeploymentTaskCallback() {
      @Override
      public void succeeded() {
        logConsumer.consume("'" + deploymentName + "' has been undeployed successfully.");
        if (deploymentImpl != null) {
          synchronized (deploymentsMap) {
            if (deploymentImpl.changeState(DeploymentStatus.UNDEPLOYING, DeploymentStatus.NOT_DEPLOYED, null, null)) {
              deploymentsMap.remove(deploymentName);
            }
          }
        }
        DeploymentLogManagerImpl logManager = myLogManagers.remove(deploymentName);
        if (logManager != null) {
          logManager.disposeLogs();
        }
        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
        computeDeployments(myRuntimeInstance, EmptyRunnable.INSTANCE);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        logConsumer.consume("Failed to undeploy '" + deploymentName + "': " + errorMessage);
        if (deploymentImpl != null) {
          synchronized (deploymentsMap) {
            deploymentImpl.changeState(DeploymentStatus.UNDEPLOYING, DeploymentStatus.DEPLOYED, errorMessage, runtime);
          }
        }
        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
      }
    });
  }

  @NotNull
  @Override
  public Collection<Deployment> getDeployments() {
    Set<Deployment> result = new LinkedHashSet<>();
    Map<Deployment, DeploymentImpl> orderedDeployments
      = new TreeMap<>(getServer().getType().getDeploymentComparator());
    synchronized (myLocalDeployments) {
      synchronized (myRemoteDeployments) {

        for (LocalDeploymentImpl localDeployment : myLocalDeployments.values()) {
          localDeployment.setRemoteDeployment(null);
          orderedDeployments.put(localDeployment, localDeployment);
        }
        result.addAll(orderedDeployments.keySet());

        for (DeploymentImpl remoteDeployment : myRemoteDeployments.values()) {
          DeploymentImpl deployment = orderedDeployments.get(remoteDeployment);
          if (deployment != null) {
            if (deployment instanceof LocalDeploymentImpl) {
              ((LocalDeploymentImpl)deployment).setRemoteDeployment(remoteDeployment);
            }
          }
          else {
            orderedDeployments.put(remoteDeployment, remoteDeployment);
          }
        }
        result.addAll(orderedDeployments.keySet());
      }
    }
    return result;
  }

  @Override
  public void connectIfNeeded(final ServerConnector.ConnectionCallback<D> callback) {
    final ServerRuntimeInstance<D> instance = myRuntimeInstance;
    if (instance != null) {
      callback.connected(instance);
      return;
    }

    setStatus(ConnectionStatus.CONNECTING);
    myConnector.connect(new ServerConnector.ConnectionCallback<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        setStatus(ConnectionStatus.CONNECTED);
        myRuntimeInstance = instance;
        callback.connected(instance);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        setStatus(ConnectionStatus.DISCONNECTED, errorMessage);
        myRuntimeInstance = null;
        callback.errorOccurred(errorMessage);
      }
    });
  }

  private void setStatus(final ConnectionStatus status) {
    setStatus(status, null);
  }

  private void setStatus(final ConnectionStatus status, String statusText) {
    myStatus = status;
    myStatusText = statusText;
    myEventDispatcher.queueConnectionStatusChanged(this);
  }

  public void changeDeploymentState(Runnable stateChanger) {
    synchronized (myLocalDeployments) {
      synchronized (myRemoteDeployments) {
        stateChanger.run();
      }
    }
    myEventDispatcher.queueDeploymentsChanged(this);
  }

  private static abstract class ConnectionCallbackBase<D extends DeploymentConfiguration> implements ServerConnector.ConnectionCallback<D> {
    @Override
    public void errorOccurred(@NotNull String errorMessage) {
    }
  }

  private class DeploymentOperationCallbackImpl implements ServerRuntimeInstance.DeploymentOperationCallback {
    private final String myDeploymentName;
    private final DeploymentTaskImpl<D> myDeploymentTask;
    private final LoggingHandlerImpl myLoggingHandler;
    private final DeploymentImpl myDeployment;

    public DeploymentOperationCallbackImpl(String deploymentName,
                                           DeploymentTaskImpl<D> deploymentTask,
                                           LoggingHandlerImpl handler,
                                           DeploymentImpl deployment) {
      myDeploymentName = deploymentName;
      myDeploymentTask = deploymentTask;
      myLoggingHandler = handler;
      myDeployment = deployment;
    }

    @Override
    public Deployment succeeded(@NotNull DeploymentRuntime deploymentRuntime) {
      myLoggingHandler.printlnSystemMessage("'" + myDeploymentName + "' has been deployed successfully.");
      myDeployment.changeState(DeploymentStatus.DEPLOYING, DeploymentStatus.DEPLOYED, null, deploymentRuntime);
      myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
      DebugConnector<?, ?> debugConnector = myDeploymentTask.getDebugConnector();
      if (debugConnector != null) {
        launchDebugger(debugConnector, deploymentRuntime);
      }
      return myDeployment;
    }

    private <D extends DebugConnectionData, R extends DeploymentRuntime> void launchDebugger(@NotNull final DebugConnector<D, R> debugConnector,
                                                                                             @NotNull DeploymentRuntime runtime) {
      try {
        final D debugInfo = debugConnector.getConnectionData((R)runtime);
        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            debugConnector.getLauncher().startDebugSession(debugInfo, myDeploymentTask.getExecutionEnvironment(), myServer);
          }
          catch (ExecutionException e) {
            myLoggingHandler.print("Cannot start debugger: " + e.getMessage() + "\n");
            LOG.info(e);
          }
        });
      }
      catch (DebugConnectionDataNotAvailableException e) {
        myLoggingHandler.print("Cannot retrieve debug connection: " + e.getMessage() + "\n");
        LOG.info(e);
      }
    }

    @Override
    public void errorOccurred(@NotNull String errorMessage) {
      myLoggingHandler.printlnSystemMessage("Failed to deploy '" + myDeploymentName + "': " + errorMessage);
      synchronized (myLocalDeployments) {
        myDeployment.changeState(DeploymentStatus.DEPLOYING, DeploymentStatus.NOT_DEPLOYED, errorMessage, null);
      }
      myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
    }
  }

  private class ChangeListener implements Runnable {

    @Override
    public void run() {
      myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
    }
  }
}
