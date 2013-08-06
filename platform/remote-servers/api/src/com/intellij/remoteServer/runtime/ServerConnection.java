package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ServerConnection<D extends DeploymentConfiguration> {
  @NotNull
  RemoteServer<?> getServer();

  @NotNull
  ConnectionStatus getStatus();

  @NotNull
  String getStatusText();


  void connect(@NotNull Runnable onFinished);


  void deploy(@NotNull DeploymentTask<D> task);

  void undeploy(@NotNull DeploymentTask<D> task);

  @NotNull
  DeploymentStatus getDeploymentStatus(@NotNull DeploymentSource source);
}
