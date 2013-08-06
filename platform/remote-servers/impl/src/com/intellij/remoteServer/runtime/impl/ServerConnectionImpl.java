package com.intellij.remoteServer.runtime.impl;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.util.ParameterizedRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nik
 */
public class ServerConnectionImpl<D extends DeploymentConfiguration> implements ServerConnection<D> {
  private final RemoteServer<?> myServer;
  private final ServerConnector<D> myConnector;
  private volatile ConnectionStatus myStatus = ConnectionStatus.DISCONNECTED;
  private volatile String myStatusText;
  private volatile ServerRuntimeInstance<D> myRuntimeInstance;
  private final ConcurrentHashMap<DeploymentSource, DeploymentInformation> myDeploymentInfos = new ConcurrentHashMap<DeploymentSource, DeploymentInformation>();

  public ServerConnectionImpl(RemoteServer<?> server, ServerConnector<D> connector) {
    myServer = server;
    myConnector = connector;
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
    return myStatusText;
  }

  @Override
  public void deploy(@NotNull final DeploymentSource source, @NotNull final D configuration) {
    runOnInstance(new ParameterizedRunnable<ServerRuntimeInstance<D>>() {
      @Override
      public void run(ServerRuntimeInstance<D> instance) {
        myDeploymentInfos.put(source, new DeploymentInformation(DeploymentStatus.DEPLOYING));
        instance.deploy(source, configuration, new UpdateDeploymentStatusCallback(source, DeploymentStatus.DEPLOYED,
                                                                                  DeploymentStatus.NOT_DEPLOYED));
      }
    });
  }

  @Override
  public void undeploy(@NotNull final DeploymentSource source, @NotNull final D configuration) {
    runOnInstance(new ParameterizedRunnable<ServerRuntimeInstance<D>>() {
      @Override
      public void run(ServerRuntimeInstance<D> instance) {
        instance.undeploy(source, configuration, new UpdateDeploymentStatusCallback(source, DeploymentStatus.NOT_DEPLOYED, DeploymentStatus.DEPLOYED));
      }
    });
  }

  @NotNull
  @Override
  public DeploymentStatus getDeploymentStatus(@NotNull DeploymentSource source) {
    DeploymentInformation information = myDeploymentInfos.get(source);
    return information != null ? information.getStatus() : DeploymentStatus.NOT_DEPLOYED;
  }

  private void runOnInstance(final ParameterizedRunnable<ServerRuntimeInstance<D>> action) {
    final ServerRuntimeInstance<D> instance = myRuntimeInstance;
    if (instance != null) {
      action.run(instance);
      return;
    }

    myStatus = ConnectionStatus.CONNECTING;
    myConnector.connect(new ServerConnector.ConnectionCallback<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        myStatus = ConnectionStatus.CONNECTED;
        myRuntimeInstance = instance;
        action.run(instance);
      }

      @Override
      public void connectionFailed(@NotNull String errorMessage) {
        myStatus = ConnectionStatus.DISCONNECTED;
        myRuntimeInstance = null;
        myStatusText = errorMessage;
      }
    });
  }

  private class UpdateDeploymentStatusCallback implements ServerRuntimeInstance.DeploymentOperationCallback {
    private final DeploymentSource mySource;
    private DeploymentStatus mySuccessStatus;
    private DeploymentStatus myFailedStatus;

    public UpdateDeploymentStatusCallback(DeploymentSource source, final DeploymentStatus successStatus, final DeploymentStatus failedStatus) {
      mySource = source;
      mySuccessStatus = successStatus;
      myFailedStatus = failedStatus;
    }

    @Override
    public void succeeded() {
      myDeploymentInfos.put(mySource, new DeploymentInformation(mySuccessStatus));
    }

    @Override
    public void failed(@NotNull String errorMessage) {
      myDeploymentInfos.put(mySource, new DeploymentInformation(myFailedStatus, errorMessage));
    }
  }
}
