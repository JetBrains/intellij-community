package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.impl.runtime.ServerConnectionImpl;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeploymentImpl<D extends DeploymentConfiguration> implements Deployment {
  private final ServerConnectionImpl<D> myConnection;
  private final String myName;
  private final DeploymentTask<D> myDeploymentTask;
  private volatile DeploymentState myState;
  private String myPresentableName;

  public DeploymentImpl(@NotNull ServerConnectionImpl<D> connection,
                        @NotNull String name,
                        @NotNull DeploymentStatus status,
                        @Nullable String statusText,
                        @Nullable DeploymentRuntime runtime,
                        @Nullable DeploymentTask<D> deploymentTask) {
    myConnection = connection;
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
    return statusText != null ? statusText : myState.getStatus().getPresentableText();
  }

  public DeploymentRuntime getRuntime() {
    return myState.getRuntime();
  }

  @Nullable
  @Override
  public DeploymentTask<D> getDeploymentTask() {
    return myDeploymentTask;
  }

  @NotNull
  @Override
  public DeploymentLogManager getOrCreateLogManager(@NotNull Project project) {
    return myConnection.getOrCreateLogManager(project, this);
  }

  @Override
  public void setStatus(@NotNull final DeploymentStatus status, @Nullable final String statusText) {
    myConnection.changeDeploymentState(this, getRuntime(), myState.getStatus(), status, statusText);
  }

  @NotNull
  @Override
  public ServerConnection<?> getConnection() {
    return myConnection;
  }

  @Nullable
  @Override
  public DeploymentRuntime getParentRuntime() {
    DeploymentRuntime runtime = getRuntime();
    return runtime == null ? null : runtime.getParent();
  }

  public boolean changeState(@NotNull DeploymentStatus oldStatus, @NotNull DeploymentStatus newStatus, @Nullable String statusText,
                             @Nullable DeploymentRuntime runtime) {
    if (myState.getStatus() == oldStatus) {
      myState = new DeploymentState(newStatus, statusText, runtime);
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myPresentableName == null ? getName() : myPresentableName;
  }

  public void setPresentableName(String presentableName) {
    myPresentableName = presentableName;
  }

  protected static class DeploymentState {
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
