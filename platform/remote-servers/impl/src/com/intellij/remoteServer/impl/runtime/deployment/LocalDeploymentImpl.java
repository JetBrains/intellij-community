/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime.deployment;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.impl.runtime.ServerConnectionImpl;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalDeploymentImpl<D extends DeploymentConfiguration> extends DeploymentImpl<D> {

  private final ServerRuntimeInstance<D> myServerInstance;
  private DeploymentImpl myRemoteDeployment;

  public LocalDeploymentImpl(@NotNull ServerRuntimeInstance<D> instance,
                             @NotNull ServerConnectionImpl<D> connection,
                             @NotNull DeploymentStatus status,
                             @Nullable String statusText,
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

  private boolean isLocalState() {
    return myRemoteDeployment == null || super.getStatus().isTransition();
  }

  @NotNull
  @Override
  public DeploymentStatus getStatus() {
    return isLocalState() ? super.getStatus() : myRemoteDeployment.getStatus();
  }

  @NotNull
  @Override
  public String getStatusText() {
    return isLocalState() ? super.getStatusText() : myRemoteDeployment.getStatusText();
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
