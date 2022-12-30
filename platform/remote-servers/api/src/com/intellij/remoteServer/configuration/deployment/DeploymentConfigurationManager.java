// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DeploymentConfigurationManager {
  
  public static @NotNull DeploymentConfigurationManager getInstance(@NotNull Project project) {
    return project.getService(DeploymentConfigurationManager.class);
  }

  public abstract @NotNull List<RunnerAndConfigurationSettings> getDeploymentConfigurations(@NotNull ServerType<?> serverType);

  public abstract void createAndRunConfiguration(@NotNull ServerType<?> serverType,
                                                 @Nullable RemoteServer<?> remoteServer,
                                                 @Nullable DeploymentSourceType<?> sourceType);
}
