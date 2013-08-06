package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.deployment.DeploymentSource;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ServerRuntimeInstance<D extends DeploymentConfiguration> {

  public abstract void deploy(@NotNull DeploymentSource source, @NotNull D configuration,
                              @NotNull DeploymentOperationCallback callback);

  public abstract void undeploy(@NotNull DeploymentSource source, @NotNull D configuration,
                                @NotNull DeploymentOperationCallback callback);

  public interface DeploymentOperationCallback {
    void succeeded();

    void failed(@NotNull String errorMessage);
  }
}
