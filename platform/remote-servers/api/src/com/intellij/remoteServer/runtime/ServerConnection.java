package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.util.ParameterizedRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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


  void disconnect();

  void deploy(@NotNull DeploymentTask<D> task, @NotNull ParameterizedRunnable<String> onDeploymentStarted);

  void computeDeployments(@NotNull Runnable onFinished);

  void undeploy(@NotNull Deployment deployment, @NotNull DeploymentRuntime runtime);

  @NotNull
  Collection<Deployment> getDeployments();

  @Nullable
  DeploymentLogManager getLogManager(@NotNull Deployment deployment);

  void connectIfNeeded(ServerConnector.ConnectionCallback<D> callback);
}
