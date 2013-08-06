package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
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


  void deploy(@NotNull DeploymentSource source, @NotNull D configuration);

  void undeploy(@NotNull DeploymentSource source, @NotNull D configuration);

  @NotNull
  DeploymentStatus getDeploymentStatus(@NotNull DeploymentSource source);
}
