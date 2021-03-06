package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ServerRuntimeInstance<D extends DeploymentConfiguration> {

  public abstract void deploy(@NotNull DeploymentTask<D> task, @NotNull DeploymentLogManager logManager,
                              @NotNull DeploymentOperationCallback callback);

  public abstract void computeDeployments(@NotNull ComputeDeploymentsCallback callback);

  @NotNull
  @Nls
  public String getDeploymentName(@NotNull DeploymentSource source, @NotNull D configuration) {
    return source.getPresentableName();
  }

  @NotNull
  @Nls
  public String getRuntimeDeploymentName(@NotNull DeploymentRuntime deploymentRuntime,
                                         @NotNull DeploymentSource source, @NotNull D configuration) {
    return getDeploymentName(source, configuration);
  }

  public abstract void disconnect();

  public interface DeploymentOperationCallback extends RemoteOperationCallback {
    default void started(@NotNull DeploymentRuntime deploymentRuntime) {
      //
    }

    Deployment succeeded(@NotNull DeploymentRuntime deploymentRuntime);
  }

  public interface ComputeDeploymentsCallback extends RemoteOperationCallback {
    void addDeployment(@NotNull String deploymentName);

    void addDeployment(@NotNull String deploymentName, @Nullable DeploymentRuntime deploymentRuntime);

    Deployment addDeployment(@NotNull String deploymentName,
                             @Nullable DeploymentRuntime deploymentRuntime,
                             @Nullable DeploymentStatus deploymentStatus,
                             @Nullable String deploymentStatusText);

    void succeeded();
  }
}
