// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.impl.runtime.ServerConnectionImpl;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LocalDeploymentImpl<D extends DeploymentConfiguration> extends DeploymentImpl<D> {

  private final ServerRuntimeInstance<D> myServerInstance;
  private DeploymentImpl myRemoteDeployment;

  public LocalDeploymentImpl(@NotNull ServerRuntimeInstance<D> instance,
                             @NotNull ServerConnectionImpl<D> connection,
                             @NotNull DeploymentStatus status,
                             @Nullable @Nls String statusText,
                             @Nullable DeploymentRuntime runtime,
                             @NotNull DeploymentTask<D> deploymentTask) {
    super(connection,
          instance.getDeploymentName(deploymentTask.getSource(), deploymentTask.getConfiguration()),
          status,
          statusText,
          runtime,
          deploymentTask);
    myServerInstance = instance;
  }

  public void setRemoteDeployment(DeploymentImpl remoteDeployment) {
    myRemoteDeployment = remoteDeployment;
    String presentableName = null;
    if (remoteDeployment != null) {
      DeploymentRuntime deploymentRuntime = remoteDeployment.getRuntime();
      DeploymentTask<D> task = getDeploymentTask();
      if (deploymentRuntime != null) {
        presentableName = myServerInstance.getRuntimeDeploymentName(deploymentRuntime, task.getSource(), task.getConfiguration());
      }
    }
    setPresentableName(presentableName);
  }

  @Override
  public @NotNull DeploymentTask<D> getDeploymentTask() {
    return Objects.requireNonNull(super.getDeploymentTask());
  }

  private boolean isLocalState() {
    return myRemoteDeployment == null || super.getStatus().isTransition();
  }

  @Override
  public @NotNull DeploymentStatus getStatus() {
    return isLocalState() ? super.getStatus() : myRemoteDeployment.getStatus();
  }

  @Override
  public @NotNull String getStatusText() {
    return isLocalState() ? super.getStatusText() : myRemoteDeployment.getStatusText();
  }

  public @Nullable DeploymentRuntime getRemoteRuntime() {
    return isLocalState() ? null : myRemoteDeployment.getRuntime();
  }

  @Override
  public boolean changeState(@NotNull DeploymentStatus oldStatus,
                             @NotNull DeploymentStatus newStatus,
                             @Nullable String statusText,
                             @Nullable DeploymentRuntime runtime) {
    boolean result = super.changeState(oldStatus, newStatus, statusText, runtime);
    if (result && myRemoteDeployment != null) {
      myRemoteDeployment.changeState(myRemoteDeployment.getStatus(), newStatus, statusText, myRemoteDeployment.getRuntime());
    }
    return result;
  }

  public boolean hasRemoteDeloyment() {
    return myRemoteDeployment != null;
  }
}
