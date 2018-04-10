// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationManager;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class DeploymentConfigurationManagerImpl extends DeploymentConfigurationManager {
  private final Project myProject;

  public DeploymentConfigurationManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<RunnerAndConfigurationSettings> getDeploymentConfigurations(@NotNull ServerType<?> serverType) {
    final DeployToServerConfigurationType configurationType =
      DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(serverType);
    return RunManager.getInstance(myProject).getConfigurationSettingsList(configurationType);
  }

  @Override
  @Deprecated
  public void createAndRunConfiguration(@NotNull ServerType<?> serverType,
                                        @Nullable RemoteServer<?> remoteServer) {
    createAndRunConfiguration(serverType, remoteServer, null);
  }

  @Override
  public void createAndRunConfiguration(@NotNull ServerType<?> serverType,
                                        @Nullable RemoteServer<?> remoteServer,
                                        @Nullable DeploymentSourceType sourceType) {
    DeployToServerConfigurationType configurationType = DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(serverType);
    RunManager runManager = RunManager.getInstance(myProject);
    ConfigurationFactoryEx factory = configurationType.getFactoryForType(sourceType);
    RunnerAndConfigurationSettings settings = runManager.createRunConfiguration(configurationType.getDisplayName(), factory);
    factory.onNewConfigurationCreated(settings.getConfiguration());
    DeployToServerRunConfiguration<?, ?> runConfiguration = (DeployToServerRunConfiguration<?, ?>)settings.getConfiguration();
    if (remoteServer != null) {
      runConfiguration.setServerName(remoteServer.getName());
    }
    if (RunDialog.editConfiguration(myProject, settings, "Create Deployment Configuration",
                                    DefaultRunExecutor.getRunExecutorInstance())) {
      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
      ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }
  }
}
