package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeploymentImpl implements Deployment {
  private final String myName;
  private final DeploymentTask<?> myDeploymentTask;
  private volatile DeploymentState myState;

  public DeploymentImpl(@NotNull String name, @NotNull DeploymentStatus status, @Nullable String statusText,
                        @Nullable DeploymentRuntime runtime, @Nullable DeploymentTask<?> deploymentTask) {
    myName = name;
    myDeploymentTask = deploymentTask;
    myState = new DeploymentState(status, statusText, runtime);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public DeploymentStatus getStatus() {
    return myState.getStatus();
  }

  @NotNull
  public String getStatusText() {
    String statusText = myState.getStatusText();
    return statusText != null ? statusText : getStatus().getPresentableText();
  }

  public DeploymentRuntime getRuntime() {
    return myState.getRuntime();
  }

  @Nullable
  @Override
  public DeploymentTask<?> getDeploymentTask() {
    return myDeploymentTask;
  }

  public boolean changeState(@NotNull DeploymentStatus oldStatus, @NotNull DeploymentStatus newStatus, @Nullable String statusText,
                             @Nullable DeploymentRuntime runtime) {
    if (myState.getStatus() == oldStatus) {
      myState = new DeploymentState(newStatus, statusText, runtime);
      return true;
    }
    return false;
  }

  private static class DeploymentState {
    private final DeploymentStatus myStatus;
    private final String myStatusText;
    private final DeploymentRuntime myRuntime;

    private DeploymentState(@NotNull DeploymentStatus status, @Nullable String statusText, @Nullable DeploymentRuntime runtime) {
      myStatus = status;
      myStatusText = statusText;
      myRuntime = runtime;
    }

    @NotNull
    public DeploymentStatus getStatus() {
      return myStatus;
    }

    @Nullable
    public String getStatusText() {
      return myStatusText;
    }

    @Nullable
    public DeploymentRuntime getRuntime() {
      return myRuntime;
    }
  }
}
