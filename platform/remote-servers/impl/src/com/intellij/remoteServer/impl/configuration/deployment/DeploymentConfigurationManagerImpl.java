// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationManager;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class DeploymentConfigurationManagerImpl extends DeploymentConfigurationManager {

  private final @NotNull Project myProject;

  DeploymentConfigurationManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<RunnerAndConfigurationSettings> getDeploymentConfigurations(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType<?> configurationType = DeployToServerConfigurationTypesRegistrar.getInstance()
      .getConfigurationType(serverType);
    return RunManager.getInstance(myProject).getConfigurationSettingsList(configurationType);
  }

  @Override
  public void createAndRunConfiguration(@NotNull ServerType<?> serverType,
                                        @Nullable RemoteServer<?> remoteServer,
                                        @Nullable DeploymentSourceType<?> sourceType) {
    DeployToServerConfigurationType<?> configurationType = DeployToServerConfigurationTypesRegistrar.getInstance()
      .getConfigurationType(serverType);
    RunManager runManager = RunManager.getInstance(myProject);
    ConfigurationFactory factory = configurationType.getFactoryForType(sourceType);
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(configurationType.getDisplayName(), factory);
    DeployToServerRunConfiguration<?, ?> runConfiguration = (DeployToServerRunConfiguration<?, ?>)settings.getConfiguration();
    runConfiguration.onNewConfigurationCreated();
    if (remoteServer != null) {
      runConfiguration.setServerName(remoteServer.getName());
    }
    if (RunDialog.editConfiguration(myProject, settings, CloudBundle.message("dialog.title.create.deployment.configuration"),
                                    DefaultRunExecutor.getRunExecutorInstance())) {
      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
      ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }
  }
}
