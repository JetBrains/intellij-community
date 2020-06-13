// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServerConnectionImpl<D extends DeploymentConfiguration> implements ServerConnection<D> {
  private static final Logger LOG = Logger.getInstance(ServerConnectionImpl.class);
  private final RemoteServer<?> myServer;
  private final ServerConnector<D> myConnector;
  private final ServerConnectionEventDispatcher myEventDispatcher;
  private final ServerConnectionManagerImpl myConnectionManager;
  private MessageBusConnection myMessageBusConnection;

  private volatile ConnectionStatus myStatus = ConnectionStatus.DISCONNECTED;
  private volatile String myStatusText;
  private volatile ServerRuntimeInstance<D> myRuntimeInstance;
  private final Map<Project, LogManagersForProject> myPerProjectLogManagers = new ConcurrentHashMap<>();
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

  @Override
  public @NotNull RemoteServer<?> getServer() {
    return myServer;
  }

  @Override
  public @NotNull ConnectionStatus getStatus() {
    return myStatus;
  }

  @Override
  public @NotNull String getStatusText() {
    return myStatusText != null ? myStatusText : myStatus.getPresentableText();
  }

  @Override
  public void connect(final @NotNull Runnable onFinished) {
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
      for (LogManagersForProject forNextProject : myPerProjectLogManagers.values()) {
        forNextProject.disposeAllLogs();
      }
      if (myMessageBusConnection != null) {
        myMessageBusConnection.disconnect();
        myMessageBusConnection = null;
      }
    }
  }

  @Override
  public void deploy(final @NotNull DeploymentTask<D> task, final @NotNull java.util.function.Consumer<String> onDeploymentStarted) {
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

        DeploymentLogManagerImpl logManager = myPerProjectLogManagers.computeIfAbsent(task.getProject(), LogManagersForProject::new)
                                                                     .findOrCreateManager(deployment)
                                                                     .withMainHandlerVisible(true);

        LoggingHandlerImpl handler = logManager.getMainLoggingHandler();
        handler.printlnSystemMessage("Deploying '" + deploymentName + "'...");
        onDeploymentStarted.accept(deploymentName);
        instance
          .deploy(task, logManager, new DeploymentOperationCallbackImpl(deploymentName, (DeploymentTaskImpl<D>)task, handler, deployment));
      }
    });
  }

  @Override
  public @Nullable DeploymentLogManager getLogManager(@NotNull Project project, @NotNull Deployment deployment) {
    LogManagersForProject forProject = myPerProjectLogManagers.get(project);
    return forProject == null ? null : forProject.findManager(deployment);
  }

  public @NotNull DeploymentLogManager getOrCreateLogManager(@NotNull Project project, @NotNull Deployment deployment) {
    LogManagersForProject forProject = myPerProjectLogManagers.computeIfAbsent(project, LogManagersForProject::new);
    return forProject.findOrCreateManager(deployment);
  }

  @Override
  public void computeDeployments(final @NotNull Runnable onFinished) {
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
        synchronized (myCollectedDeployments) {
          myAllDeployments.replaceRemotesWith(myCollectedDeployments);
        }

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
  public void undeploy(@NotNull Deployment deployment, final @Nullable DeploymentRuntime runtime) {
    String deploymentName = deployment.getName();
    final MyDeployments.UndeployTransition undeployInProgress = myAllDeployments.startUndeploy(deploymentName);

    myEventDispatcher.queueDeploymentsChanged(this);

    final List<LoggingHandlerImpl> handlers = myPerProjectLogManagers.values().stream()
      .map(nextForProject -> nextForProject.findManager(deployment))
      .filter(Objects::nonNull)
      .map(DeploymentLogManagerImpl::getMainLoggingHandler)
      .collect(Collectors.toList());

    final Consumer<String> logConsumer = message -> {
      if (handlers.isEmpty()) {
        LOG.info(message);
      }
      else {
        handlers.forEach(h -> h.printlnSystemMessage(message));
      }
    };

    logConsumer.consume("Undeploying '" + deploymentName + "'...");

    DeploymentRuntime.UndeploymentTaskCallback undeploymentTaskCallback = new DeploymentRuntime.UndeploymentTaskCallback() {
      @Override
      public void succeeded() {
        logConsumer.consume("'" + deploymentName + "' has been undeployed successfully.");

        Set<String> namesToDispose = new LinkedHashSet<>();
        namesToDispose.add(deploymentName);

        if (undeployInProgress != null) {
          undeployInProgress.succeeded();
          undeployInProgress.getSubDeployments().forEach(deployment -> namesToDispose.add(deployment.getName()));
        }

        namesToDispose.forEach(name -> disposeAllLogs(name));

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
    };

    if (runtime == null) {
      undeploymentTaskCallback.succeeded();
    }
    else {
      runtime.undeploy(undeploymentTaskCallback);
    }
  }

  public void disposeAllLogs(@NotNull DeploymentImpl deployment) {
    disposeAllLogs(deployment.getName());
  }

  public void disposeAllLogs(@NotNull String deploymentName) {
    myPerProjectLogManagers.values().forEach(nextForProject -> nextForProject.disposeManager(deploymentName));
  }

  @Override
  public @NotNull Collection<Deployment> getDeployments() {
    return myAllDeployments.listDeployments();
  }

  private void setupProjectListener() {
    if (myMessageBusConnection == null) {
      myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
      myMessageBusConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          onProjectClosed(project);
        }
      });
    }
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
        setupProjectListener();
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

  private void onProjectClosed(@NotNull Project project) {
    myPerProjectLogManagers.remove(project);
    boolean hasChanged = myAllDeployments.removeAllLocalForProject(project);
    if (hasChanged) {
      myEventDispatcher.queueDeploymentsChanged(this);
    }
  }

  private class LogManagersForProject {
    private final Project myProject;
    private final Map<String, DeploymentLogManagerImpl> myLogManagers = new ConcurrentHashMap<>();

    LogManagersForProject(@NotNull Project project) {
      myProject = project;
    }

    public @Nullable DeploymentLogManagerImpl findManager(@NotNull Deployment deployment) {
      return myLogManagers.get(deployment.getName());
    }

    public DeploymentLogManagerImpl findOrCreateManager(@NotNull Deployment deployment) {
      return myLogManagers.computeIfAbsent(deployment.getName(), this::newDeploymentLogManager);
    }

    private DeploymentLogManagerImpl newDeploymentLogManager(String deploymentName) {
      return new DeploymentLogManagerImpl(myProject, new ChangeListener());
    }

    public void disposeManager(@NotNull String deploymentName) {
      DeploymentLogManagerImpl manager = myLogManagers.remove(deploymentName);
      if (manager != null) {
        manager.disposeLogs();
      }
    }

    public void disposeAllLogs() {
      for (DeploymentLogManagerImpl nextManager : myLogManagers.values()) {
        nextManager.disposeLogs();
      }
    }
  }

  private abstract static class ConnectionCallbackBase<D extends DeploymentConfiguration> implements ServerConnector.ConnectionCallback<D> {
    @Override
    public void errorOccurred(@NotNull String errorMessage) {
    }
  }

  private class DeploymentOperationCallbackImpl implements ServerRuntimeInstance.DeploymentOperationCallback {
    private final String myDeploymentName;
    private final DeploymentTaskImpl<D> myDeploymentTask;
    private final LoggingHandlerImpl myLoggingHandler;
    private final DeploymentImpl myDeployment;

    DeploymentOperationCallbackImpl(String deploymentName,
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

    private <D extends DebugConnectionData, R extends DeploymentRuntime> void launchDebugger(final @NotNull DebugConnector<D, R> debugConnector,
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
      myAllDeployments.updateAnyState(myDeployment, null,
                                      DeploymentStatus.DEPLOYING, DeploymentStatus.NOT_DEPLOYED, errorMessage);
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
    private final Object myLock = new Object();

    private final Map<String, DeploymentImpl> myRemoteDeployments = new HashMap<>();
    private final Map<String, LocalDeploymentImpl> myLocalDeployments = new HashMap<>();
    private List<Deployment> myCachedAllDeployments;
    private final Comparator<? super Deployment> myDeploymentComparator;

    MyDeployments(Comparator<? super Deployment> deploymentComparator) {
      myDeploymentComparator = deploymentComparator;
    }

    public void addLocal(@NotNull LocalDeploymentImpl<?> deployment) {
      synchronized (myLock) {
        myLocalDeployments.put(deployment.getName(), deployment);
        myCachedAllDeployments = null;
      }
    }

    public void replaceRemotesWith(@NotNull Collection<DeploymentImpl> newDeployments) {
      synchronized (myLock) {
        myRemoteDeployments.clear();
        myCachedAllDeployments = null;
        for (DeploymentImpl deployment : newDeployments) {
          myRemoteDeployments.put(deployment.getName(), deployment);
        }
      }
    }

    public @Nullable DeploymentImpl updateRemoteState(@NotNull String deploymentName,
                                                      @Nullable DeploymentRuntime deploymentRuntime,
                                                      @NotNull DeploymentStatus deploymentStatus,
                                                      @Nullable String deploymentStatusText) {

      synchronized (myLock) {
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

      synchronized (myLock) {
        return deployment.changeState(oldStatus, newStatus, statusText, deploymentRuntime);
      }
    }

    public @NotNull Collection<Deployment> listDeployments() {
      synchronized (myLock) {
        if (myCachedAllDeployments == null) {
          Collection<Deployment> result = doListDeployments();
          myCachedAllDeployments = Collections.unmodifiableList(new ArrayList<>(result));
        }
        return myCachedAllDeployments;
      }
    }

    private Collection<Deployment> doListDeployments() {
      //assumed myLock
      Map<Deployment, DeploymentImpl> orderedDeployments = new TreeMap<>(myDeploymentComparator);
      List<LocalDeploymentImpl> matchedLocalsBefore = new LinkedList<>();

      for (LocalDeploymentImpl localDeployment : myLocalDeployments.values()) {
        if (localDeployment.hasRemoteDeloyment()) {
          matchedLocalsBefore.add(localDeployment);
        }
        localDeployment.setRemoteDeployment(null);
        orderedDeployments.put(localDeployment, localDeployment);
      }

      Set<Deployment> result = new LinkedHashSet<>(orderedDeployments.keySet());

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

      final DeploymentStatus finishedExternally = DeploymentStatus.NOT_DEPLOYED;
      for (LocalDeploymentImpl nextLocal : matchedLocalsBefore) {
        if (!nextLocal.hasRemoteDeloyment()) {
          nextLocal.changeState(nextLocal.getStatus(), finishedExternally, null, null);
        }
      }

      result.addAll(orderedDeployments.keySet());
      return result;
    }

    public boolean removeAllLocalForProject(@NotNull Project project) {
      synchronized (myLock) {
        boolean hasChanged = false;
        for (Iterator<LocalDeploymentImpl> it = myLocalDeployments.values().iterator(); it.hasNext(); ) {
          LocalDeploymentImpl nextLocal = it.next();
          if (nextLocal.getDeploymentTask() != null && nextLocal.getDeploymentTask().getProject() == project) {
            it.remove();
            hasChanged = true;
          }
        }
        if (hasChanged) {
          myCachedAllDeployments = null;
        }
        return hasChanged;
      }
    }

    public @Nullable UndeployTransition startUndeploy(@NotNull String deploymentName) {
      synchronized (myLock) {
        DeploymentImpl deployment = myLocalDeployments.get(deploymentName);
        if (deployment == null) {
          deployment = myRemoteDeployments.get(deploymentName);
        }
        return deployment == null ? null : new UndeployTransition(deployment, collectDeepChildren(deployment));
      }
    }

    private @NotNull List<Deployment> collectDeepChildren(@NotNull Deployment root) {
      DeepChildrenCollector collector = new DeepChildrenCollector(root.getRuntime());
      synchronized (myLock) {
        for (LocalDeploymentImpl nextLocal : myLocalDeployments.values()) {
          collector.visitDeployment(nextLocal);
        }
        for (DeploymentImpl nextRemote : myRemoteDeployments.values()) {
          collector.visitDeployment(nextRemote);
        }
      }
      return collector.getChildDeployments();
    }

    private class UndeployTransition {
      private final DeploymentImpl myDeployment;
      private final List<Deployment> mySubDeployments;

      UndeployTransition(@NotNull DeploymentImpl deployment, @NotNull List<Deployment> subDeployments) {
        myDeployment = deployment;
        mySubDeployments = new ArrayList<>(subDeployments);

        myDeployment.changeState(DeploymentStatus.DEPLOYED, DeploymentStatus.DEPLOYING, null, deployment.getRuntime());
      }

      public void succeeded() {
        synchronized (myLock) {
          if (tryChangeToTerminalState(DeploymentStatus.NOT_DEPLOYED, true) || myDeployment.getRuntime() == null) {
            forgetDeployment(myDeployment);

            for (Deployment nextImplicitlyUndeployed : mySubDeployments) {
              if (nextImplicitlyUndeployed != myDeployment) {
                forgetDeployment(nextImplicitlyUndeployed);
              }
            }

            myCachedAllDeployments = null;
          }
        }
      }

      public void failed() {
        synchronized (myLock) {
          tryChangeToTerminalState(DeploymentStatus.DEPLOYED, false);
        }
      }

      public @NotNull Iterable<Deployment> getSubDeployments() {
        return mySubDeployments;
      }

      private boolean tryChangeToTerminalState(DeploymentStatus terminalState, boolean forgetRuntime) {
        //assumed myLock
        DeploymentRuntime targetRuntime = forgetRuntime ? null : myDeployment.getRuntime();
        return myDeployment.changeState(DeploymentStatus.DEPLOYING, terminalState, null, targetRuntime);
      }

      private void forgetDeployment(@NotNull Deployment deployment) {
        synchronized (myLock) {
          String deploymentName = deployment.getName();
          myLocalDeployments.remove(deploymentName);
          myRemoteDeployments.remove(deploymentName);
        }
      }
    }

    private static class DeepChildrenCollector {
      private final Map<DeploymentRuntime, Boolean> mySettledStatuses = new IdentityHashMap<>();
      private final List<Deployment> myCollectedChildren = new LinkedList<>();
      private final DeploymentRuntime myRootRuntime;

      DeepChildrenCollector(DeploymentRuntime rootRuntime) {
        myRootRuntime = rootRuntime;
      }

      public void visitDeployment(@NotNull Deployment deployment) {
        if (isUnderRootRuntime(deployment.getRuntime())) {
          myCollectedChildren.add(deployment);
        }
      }

      private boolean isUnderRootRuntime(@Nullable DeploymentRuntime runtime) {
        if (runtime == null) {
          return false;
        }
        if (runtime == myRootRuntime) {
          return true;
        }
        return mySettledStatuses.computeIfAbsent(runtime, rt -> this.isUnderRootRuntime(rt.getParent()));
      }

      public List<Deployment> getChildDeployments() {
        return Collections.unmodifiableList(myCollectedChildren);
      }
    }
  }
}
