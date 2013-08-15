package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class ServerRuntimeInstance<D extends DeploymentConfiguration> {

  public abstract void deploy(@NotNull DeploymentTask<D> task, @NotNull DeploymentOperationCallback callback);

  public abstract void computeDeployments(@NotNull ComputeDeploymentsCallback deployments);

  @NotNull
  public String getDeploymentName(@NotNull DeploymentSource source) {
    return source.getPresentableName();
  }

  public abstract void disconnect();

  public interface DeploymentOperationCallback extends RemoteOperationCallback {
    void succeeded(@NotNull DeploymentRuntime deployment);
  }

  public interface ComputeDeploymentsCallback extends RemoteOperationCallback {
    void succeeded(@NotNull List<Deployment> deployments);
  }
}
