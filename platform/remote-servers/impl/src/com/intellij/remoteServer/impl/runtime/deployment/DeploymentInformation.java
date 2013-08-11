package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeploymentInformation {
  private final DeploymentStatus myStatus;
  private final String myStatusText;

  public DeploymentInformation(@NotNull DeploymentStatus status) {
    myStatus = status;
    myStatusText = status.name();
  }

  public DeploymentInformation(@NotNull DeploymentStatus status, @NotNull String statusText) {
    myStatus = status;
    myStatusText = statusText;
  }

  public DeploymentStatus getStatus() {
    return myStatus;
  }

  public String getStatusText() {
    return myStatusText;
  }
}
