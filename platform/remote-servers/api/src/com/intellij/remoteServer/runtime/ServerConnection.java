package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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

  void computeDeployments(@NotNull Runnable onFinished);

  @NotNull
  Collection<Deployment> getDeployments();
}
