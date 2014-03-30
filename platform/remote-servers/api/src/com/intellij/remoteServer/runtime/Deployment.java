package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface Deployment {
  @NotNull
  String getName();

  @NotNull
  DeploymentStatus getStatus();

  @NotNull
  String getStatusText();

  @Nullable
  DeploymentRuntime getRuntime();

  @Nullable
  DeploymentTask<?> getDeploymentTask();
}
