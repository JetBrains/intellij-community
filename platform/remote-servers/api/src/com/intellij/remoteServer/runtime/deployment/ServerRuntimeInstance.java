package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ServerRuntimeInstance<D extends DeploymentConfiguration> {

  public abstract void deploy(@NotNull DeploymentTask<D> task, @NotNull DeploymentOperationCallback callback);

  public abstract void undeploy(@NotNull DeploymentTask<D> task, @NotNull DeploymentOperationCallback callback);

  public interface DeploymentOperationCallback extends RemoteOperationCallback {
    void succeeded();
  }
}
