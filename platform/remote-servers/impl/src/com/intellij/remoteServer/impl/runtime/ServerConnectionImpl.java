package com.intellij.remoteServer.impl.runtime;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentInformation;
import com.intellij.remoteServer.runtime.ConnectionStatus;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
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
  public void connect(@NotNull final Runnable onFinished) {
    disconnect();
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

  private void disconnect() {
    if (myStatus == ConnectionStatus.CONNECTED) {
      myRuntimeInstance = null;
      myConnector.disconnect();
      myStatus = ConnectionStatus.DISCONNECTED;
    }
  }

  @Override
  public void deploy(@NotNull final DeploymentTask<D> task) {
    connectIfNeeded(new ConnectionCallbackBase<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        myDeploymentInfos.put(task.getSource(), new DeploymentInformation(DeploymentStatus.DEPLOYING));
        instance.deploy(task, new UpdateDeploymentStatusCallback(task.getSource(), DeploymentStatus.DEPLOYED,
                                                                    DeploymentStatus.NOT_DEPLOYED));
      }
    });
  }

  @Override
  public void undeploy(@NotNull final DeploymentTask<D> task) {
    connectIfNeeded(new ConnectionCallbackBase<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        myDeploymentInfos.put(task.getSource(), new DeploymentInformation(DeploymentStatus.UNDEPLOYING));
        instance.undeploy(task, new UpdateDeploymentStatusCallback(task.getSource(), DeploymentStatus.NOT_DEPLOYED,
                                                                      DeploymentStatus.DEPLOYED));
      }
    });
  }

  @NotNull
  @Override
  public DeploymentStatus getDeploymentStatus(@NotNull DeploymentSource source) {
    DeploymentInformation information = myDeploymentInfos.get(source);
    return information != null ? information.getStatus() : DeploymentStatus.NOT_DEPLOYED;
  }

  private void connectIfNeeded(final ServerConnector.ConnectionCallback<D> callback) {
    final ServerRuntimeInstance<D> instance = myRuntimeInstance;
    if (instance != null) {
      callback.connected(instance);
      return;
    }

    myStatus = ConnectionStatus.CONNECTING;
    myConnector.connect(new ServerConnector.ConnectionCallback<D>() {
      @Override
      public void connected(@NotNull ServerRuntimeInstance<D> instance) {
        myStatus = ConnectionStatus.CONNECTED;
        myRuntimeInstance = instance;
        callback.connected(instance);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        myStatus = ConnectionStatus.DISCONNECTED;
        myRuntimeInstance = null;
        myStatusText = errorMessage;
        callback.errorOccurred(errorMessage);
      }
    });
  }

  private static abstract class ConnectionCallbackBase<D extends DeploymentConfiguration> implements ServerConnector.ConnectionCallback<D> {
    @Override
    public void errorOccurred(@NotNull String errorMessage) {
    }
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
    public void errorOccurred(@NotNull String errorMessage) {
      myDeploymentInfos.put(mySource, new DeploymentInformation(myFailedStatus, errorMessage));
    }
  }
}
