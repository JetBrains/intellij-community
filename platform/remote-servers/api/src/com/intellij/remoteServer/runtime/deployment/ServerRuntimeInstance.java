package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ServerRuntimeInstance<D extends DeploymentConfiguration> {

  public abstract void deploy(@NotNull DeploymentTask<D> task, @NotNull DeploymentLogManager logManager,
                              @NotNull DeploymentOperationCallback callback);

  public abstract void computeDeployments(@NotNull ComputeDeploymentsCallback callback);

  @NotNull
  public String getDeploymentName(@NotNull DeploymentSource source, D configuration) {
    return getDeploymentName(source);
  }

  /**
   * @deprecated use {@link #getDeploymentName(com.intellij.remoteServer.configuration.deployment.DeploymentSource, D configuration)} instead
   */
  @NotNull
  public String getDeploymentName(@NotNull DeploymentSource source) {
    return source.getPresentableName();
  }

  @NotNull
  public String getRuntimeDeploymentName(@NotNull DeploymentRuntime deploymentRuntime, @NotNull DeploymentSource source, D configuration) {
    return getDeploymentName(source, configuration);
  }

  public abstract void disconnect();

  public interface DeploymentOperationCallback extends RemoteOperationCallback {
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
