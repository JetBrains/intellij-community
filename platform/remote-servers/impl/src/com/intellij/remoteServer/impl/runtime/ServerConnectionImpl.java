/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
  private final Map<String, DeploymentLogManagerImpl> myLogManagers = ContainerUtil.newConcurrentMap();
  private final MyDeployments myAllDeployments;

  public ServerConnectionImpl(RemoteServer<?> server,
                              ServerConnector<D> connector,
                              @Nullable ServerConnectionManagerImpl connectionManager,
                              ServerConnectionEventDispatcher eventDispatcher) {
    myServer = server;
    myConnector = connector;
    myConnectionManager = connectionManager;
    myEventDispatcher = eventDispatcher;
    myAllDeployments = new MyDeployments(server.getType().getDeploymentComparator());
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
  public void deploy(@NotNull final DeploymentTask<D> task, @NotNull final java.util.function.Consumer<String> onDeploymentStarted) {
    connectIfNeeded(new ConnectionCallbackBase<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        LocalDeploymentImpl<?> deployment = new LocalDeploymentImpl<>(instance,
                                                                      ServerConnectionImpl.this,
                                                                      DeploymentStatus.DEPLOYING,
                                                                      null,
                                                                      null,
                                                                      task);
        String deploymentName = deployment.getName();
        myAllDeployments.addLocal(deployment);

        DeploymentLogManagerImpl logManager = new DeploymentLogManagerImpl(task.getProject(), new ChangeListener())
          .withMainHandlerVisible(true);
        LoggingHandlerImpl handler = logManager.getMainLoggingHandler();
        myLogManagers.put(deploymentName, logManager);
        handler.printlnSystemMessage("Deploying '" + deploymentName + "'...");
        onDeploymentStarted.accept(deploymentName);
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
      private final List<DeploymentImpl> myCollectedDeployments = Collections.synchronizedList(new ArrayList<>());

      @Override
      public void addDeployment(@NotNull String deploymentName) {
        addDeployment(deploymentName, null);
      }

      @Override
      public void addDeployment(@NotNull String deploymentName, @Nullable DeploymentRuntime deploymentRuntime) {
        addDeployment(deploymentName, deploymentRuntime, null, null);
      }

      @Override
      public Deployment addDeployment(@NotNull String name,
                                      @Nullable DeploymentRuntime runtime,
                                      @Nullable DeploymentStatus status,
                                      @Nullable String statusText) {
        if (status == null) {
          status = DeploymentStatus.DEPLOYED;
        }
        DeploymentImpl result = myAllDeployments.updateRemoteState(name, runtime, status, statusText);
        if (result == null) {
          result = new DeploymentImpl<>(ServerConnectionImpl.this,
                                        name,
                                        status,
                                        statusText,
                                        runtime,
                                        null);
        }
        myCollectedDeployments.add(result);
        return result;
      }

      @Override
      public void succeeded() {
        myAllDeployments.replaceRemotesWith(myCollectedDeployments);

        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
        onFinished.run();
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        myAllDeployments.replaceRemotesWith(Collections.emptyList());

        myStatusText = "Cannot obtain deployments: " + errorMessage;
        myEventDispatcher.queueConnectionStatusChanged(ServerConnectionImpl.this);
        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
        onFinished.run();
      }
    });
  }

  @Override
  public void undeploy(@NotNull Deployment deployment, @NotNull final DeploymentRuntime runtime) {
    String deploymentName = deployment.getName();
    final StateTransition undeployInProgress = myAllDeployments.startUndeploy(deploymentName);

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

        if (undeployInProgress != null) {
          undeployInProgress.succeeded();
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

        if (undeployInProgress != null) {
          undeployInProgress.failed();
        }

        myEventDispatcher.queueDeploymentsChanged(ServerConnectionImpl.this);
      }
    });
  }

  @NotNull
  @Override
  public Collection<Deployment> getDeployments() {
    return myAllDeployments.listDeployments();
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

  public void changeDeploymentState(@NotNull DeploymentImpl deployment,
                                    @Nullable DeploymentRuntime deploymentRuntime,
                                    @NotNull DeploymentStatus oldStatus, @NotNull DeploymentStatus newStatus,
                                    @Nullable String statusText) {

    if (myAllDeployments.updateAnyState(deployment, deploymentRuntime, oldStatus, newStatus, statusText)) {
      myEventDispatcher.queueDeploymentsChanged(this);
    }
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
      synchronized (myAllDeployments.LOCAL_LOCK) {
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

  private static class MyDeployments {
    private final Object LOCAL_LOCK = new Object();
    private final Object REMOTE_LOCK = new Object();

    private final Map<String, DeploymentImpl> myRemoteDeployments = new HashMap<>();
    private final Map<String, LocalDeploymentImpl> myLocalDeployments = new HashMap<>();
    private List<Deployment> myCachedAllDeployments;
    private final Comparator<Deployment> myDeploymentComparator;

    public MyDeployments(Comparator<Deployment> deploymentComparator) {
      myDeploymentComparator = deploymentComparator;
    }

    public void addLocal(@NotNull LocalDeploymentImpl<?> deployment) {
      synchronized (LOCAL_LOCK) {
        myLocalDeployments.put(deployment.getName(), deployment);
        myCachedAllDeployments = null;
      }
    }

    public void replaceRemotesWith(@NotNull Collection<DeploymentImpl> newDeployments) {
      synchronized (REMOTE_LOCK) {
        myRemoteDeployments.clear();
        myCachedAllDeployments = null;
        for (DeploymentImpl deployment : newDeployments) {
          myRemoteDeployments.put(deployment.getName(), deployment);
        }
      }
    }

    @Nullable
    public DeploymentImpl updateRemoteState(@NotNull String deploymentName,
                                            @Nullable DeploymentRuntime deploymentRuntime,
                                            @NotNull DeploymentStatus deploymentStatus,
                                            @Nullable String deploymentStatusText) {

      synchronized (REMOTE_LOCK) {
        DeploymentImpl result = myRemoteDeployments.get(deploymentName);
        if (result != null && !result.getStatus().isTransition()) {
          result.changeState(result.getStatus(), deploymentStatus, deploymentStatusText, deploymentRuntime);
        }
        return result;
      }
    }

    public boolean updateAnyState(@NotNull DeploymentImpl deployment,
                                  @Nullable DeploymentRuntime deploymentRuntime,
                                  @NotNull DeploymentStatus oldStatus,
                                  @NotNull DeploymentStatus newStatus,
                                  @Nullable String statusText) {

      synchronized (LOCAL_LOCK) {
        synchronized (REMOTE_LOCK) {
          return deployment.changeState(oldStatus, newStatus, statusText, deploymentRuntime);
        }
      }
    }

    @NotNull
    public Collection<Deployment> listDeployments() {
      synchronized (LOCAL_LOCK) {
        synchronized (REMOTE_LOCK) {
          if (myCachedAllDeployments == null) {
            Collection<Deployment> result = doListDeployments();
            myCachedAllDeployments = Collections.unmodifiableList(new ArrayList<>(result));
          }

          return myCachedAllDeployments;
        }
      }
    }

    private Collection<Deployment> doListDeployments() {
      //assumed both LOCAL_LOCK and REMOTE_LOCK
      Set<Deployment> result = new LinkedHashSet<>();
      Map<Deployment, DeploymentImpl> orderedDeployments = new TreeMap<>(myDeploymentComparator);

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
      return result;
    }

    @SuppressWarnings("Duplicates")
    @Nullable
    public StateTransition startUndeploy(@NotNull String deploymentName) {

      synchronized (LOCAL_LOCK) {
        synchronized (REMOTE_LOCK) {

          DeploymentImpl local = myLocalDeployments.get(deploymentName);
          if (local != null) {
            return new UndeployTransition(local) {
              @Override
              public void succeeded() {
                synchronized (LOCAL_LOCK) {
                  if (completeStateChange()) {
                    myLocalDeployments.remove(getDeployment().getName());
                    myCachedAllDeployments = null;
                  }
                }
              }

              @Override
              public void failed() {
                synchronized (LOCAL_LOCK) {
                  rollbackStateChange();
                }
              }
            };
          }
          DeploymentImpl remote = myRemoteDeployments.get(deploymentName);
          if (remote != null) {
            return new UndeployTransition(remote) {
              @Override
              public void succeeded() {
                synchronized (REMOTE_LOCK) {
                  if (completeStateChange()) {
                    myRemoteDeployments.remove(getDeployment().getName());
                    myCachedAllDeployments = null;
                  }
                }
              }

              @Override
              public void failed() {
                synchronized (REMOTE_LOCK) {
                  rollbackStateChange();
                }
              }
            };
          }
          return null;
        }
      }
    }

    private static abstract class UndeployTransition extends StateTransitionImpl {
      public UndeployTransition(@NotNull DeploymentImpl deployment) {
        super(deployment, DeploymentStatus.DEPLOYED, DeploymentStatus.DEPLOYING, DeploymentStatus.NOT_DEPLOYED);
      }
    }

    private static abstract class StateTransitionImpl implements StateTransition {
      private final DeploymentImpl myDeployment;
      private final DeploymentStatus myStartStatus;
      private final DeploymentStatus myInProgressStatus;
      private final DeploymentStatus myEndStatus;

      public StateTransitionImpl(@NotNull DeploymentImpl deployment,
                                 @NotNull DeploymentStatus start, @NotNull DeploymentStatus inProgress, @NotNull DeploymentStatus end) {

        myDeployment = deployment;
        myStartStatus = start;
        myEndStatus = end;
        myInProgressStatus = inProgress;
        //
        myDeployment.changeState(start, inProgress, null, null);
      }

      @NotNull
      protected final DeploymentImpl getDeployment() {
        return myDeployment;
      }

      protected boolean completeStateChange() {
        return myDeployment.changeState(myInProgressStatus, myEndStatus, null, null);
      }

      protected boolean rollbackStateChange() {
        return myDeployment.changeState(myInProgressStatus, myStartStatus, null, null);
      }
    }
  }

  private interface StateTransition {
    void succeeded();

    void failed();
  }
}
