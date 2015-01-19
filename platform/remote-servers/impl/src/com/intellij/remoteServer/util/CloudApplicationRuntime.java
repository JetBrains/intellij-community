/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CloudApplicationRuntime extends DeploymentRuntime {

  private final String myApplicationName;
  private Deployment myDeployment;

  public CloudApplicationRuntime(String applicationName) {
    myApplicationName = applicationName;
  }

  public String getApplicationName() {
    return myApplicationName;
  }

  @Nullable
  public DeploymentStatus getStatus() {
    return null;
  }

  @Nullable
  public String getStatusText() {
    return null;
  }

  public void setDeploymentModel(@NotNull Deployment deployment) {
    myDeployment = deployment;
  }

  protected Deployment getDeploymentModel() {
    return myDeployment;
  }
}
