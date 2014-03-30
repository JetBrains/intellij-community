package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ServerConnector<D extends DeploymentConfiguration> {
  public abstract void connect(@NotNull ConnectionCallback<D> callback);

  public interface ConnectionCallback<D extends DeploymentConfiguration> extends RemoteOperationCallback {
    void connected(@NotNull ServerRuntimeInstance<D> serverRuntimeInstance);
  }
}
