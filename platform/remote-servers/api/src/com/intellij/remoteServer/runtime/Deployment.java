package com.intellij.remoteServer.runtime;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Deployment {
  @NotNull
  String getName();

  @NotNull
  @Nls
  String getPresentableName();

  @NotNull
  DeploymentStatus getStatus();

  @NotNull
  @Nls
  String getStatusText();

  @Nullable
  DeploymentRuntime getRuntime();

  @Nullable
  DeploymentRuntime getParentRuntime();

  @Nullable
  DeploymentTask<?> getDeploymentTask();

  @NotNull
  DeploymentLogManager getOrCreateLogManager(@NotNull Project project);

  void setStatus(@NotNull DeploymentStatus status, @Nullable String statusText);

  @NotNull
  ServerConnection<?> getConnection();
}
