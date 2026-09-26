// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.impl.runtime.ServerConnectionImpl;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeploymentImpl<D extends DeploymentConfiguration> implements Deployment {
  private final ServerConnectionImpl<D> myConnection;
  private final String myName;
  private final DeploymentTask<D> myDeploymentTask;
  private volatile DeploymentState myState;
  private @Nls String myPresentableName;

  public DeploymentImpl(@NotNull ServerConnectionImpl<D> connection,
                        @NotNull String name,
                        @NotNull DeploymentStatus status,
                        @Nullable @Nls String statusText,
                        @Nullable DeploymentRuntime runtime,
                        @Nullable DeploymentTask<D> deploymentTask) {
    myConnection = connection;
    myName = name;
    myDeploymentTask = deploymentTask;
    myState = new DeploymentState(status, statusText, runtime);
  }

  @Override
  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  @Override
  public @NotNull DeploymentStatus getStatus() {
    return myState.getStatus();
  }

  @Override
  public @NotNull @Nls String getStatusText() {
    String statusText = myState.getStatusText();
    return statusText != null ? statusText : myState.getStatus().getPresentableText();
  }

  @Override
  public DeploymentRuntime getRuntime() {
    return myState.getRuntime();
  }

  @Override
  public @Nullable DeploymentTask<D> getDeploymentTask() {
    return myDeploymentTask;
  }

  @Override
  public @NotNull DeploymentLogManager getOrCreateLogManager(@NotNull Project project) {
    return myConnection.getOrCreateLogManager(project, this);
  }

  public void disposeAllLogs() {
    myConnection.disposeAllLogs(this);
  }

  @Override
  public void setStatus(final @NotNull DeploymentStatus status, final @Nullable @Nls String statusText) {
    myConnection.changeDeploymentState(this, getRuntime(), myState.getStatus(), status, statusText);
  }

  @Override
  public @NotNull ServerConnection<?> getConnection() {
    return myConnection;
  }

  @Override
  public @Nullable DeploymentRuntime getParentRuntime() {
    DeploymentRuntime runtime = getRuntime();
    return runtime == null ? null : runtime.getParent();
  }

  public boolean changeState(@NotNull DeploymentStatus oldStatus,
                             @NotNull DeploymentStatus newStatus,
                             @Nullable @Nls String statusText,
                             @Nullable DeploymentRuntime runtime) {
    if (myState.getStatus() == oldStatus) {
      myState = new DeploymentState(newStatus, statusText, runtime);
      return true;
    }
    return false;
  }

  @Override
  public @NotNull String getPresentableName() {
    return myPresentableName == null ? getName() : myPresentableName;
  }

  public void setPresentableName(@Nls String presentableName) {
    myPresentableName = presentableName;
  }

  protected static final class DeploymentState {
    private final DeploymentStatus myStatus;
    private final @Nls String myStatusText;
    private final DeploymentRuntime myRuntime;

    private DeploymentState(@NotNull DeploymentStatus status, @Nullable @Nls String statusText, @Nullable DeploymentRuntime runtime) {
      myStatus = status;
      myStatusText = statusText;
      myRuntime = runtime;
    }

    public @NotNull DeploymentStatus getStatus() {
      return myStatus;
    }

    public @Nullable @Nls String getStatusText() {
      return myStatusText;
    }

    public @Nullable DeploymentRuntime getRuntime() {
      return myRuntime;
    }
  }
}
