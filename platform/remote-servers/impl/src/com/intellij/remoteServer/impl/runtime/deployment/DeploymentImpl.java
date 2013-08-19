package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeploymentImpl implements Deployment {
  private final String myName;
  private final DeploymentStatus myStatus;
  private final String myStatusText;
  private final DeploymentRuntime myRuntime;

  public DeploymentImpl(@NotNull String name, @NotNull DeploymentStatus status, @Nullable String statusText, @Nullable DeploymentRuntime runtime) {
    myName = name;
    myStatus = status;
    myStatusText = statusText;
    myRuntime = runtime;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public DeploymentStatus getStatus() {
    return myStatus;
  }

  @NotNull
  public String getStatusText() {
    return myStatusText != null ? myStatusText : myStatus.getPresentableText();
  }

  public DeploymentRuntime getRuntime() {
    return myRuntime;
  }
}
