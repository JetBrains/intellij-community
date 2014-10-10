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
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class DeploymentConfigurationManager {
  @NotNull
  public static DeploymentConfigurationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DeploymentConfigurationManager.class);
  }

  @NotNull
  public abstract List<RunnerAndConfigurationSettings> getDeploymentConfigurations(@NotNull ServerType<?> serverType);

  public abstract void createAndRunConfiguration(@NotNull ServerType<?> serverType, @Nullable RemoteServer<?> remoteServer);
}
