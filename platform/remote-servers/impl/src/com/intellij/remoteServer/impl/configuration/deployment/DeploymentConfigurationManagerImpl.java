/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    final DeployToServerConfigurationType configurationType = DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(serverType);
    return RunManager.getInstance(myProject).getConfigurationSettingsList(configurationType);
  }

  @Override
  public void createAndRunConfiguration(@NotNull ServerType<?> serverType, @Nullable RemoteServer<?> remoteServer) {
    DeployToServerConfigurationType configurationType = DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(serverType);
    RunManager runManager = RunManager.getInstance(myProject);
    ConfigurationFactoryEx factory = configurationType.getFactory();
    RunnerAndConfigurationSettings settings = runManager.createRunConfiguration(configurationType.getDisplayName(), factory);
    factory.onNewConfigurationCreated(settings.getConfiguration());
    DeployToServerRunConfiguration<?, ?> runConfiguration = (DeployToServerRunConfiguration<?, ?>)settings.getConfiguration();
    if (remoteServer != null) {
      runConfiguration.setServerName(remoteServer.getName());
    }
    if (RunDialog.editConfiguration(myProject, settings, "Create Deployment Configuration",
                                    DefaultRunExecutor.getRunExecutorInstance())) {
      runManager.addConfiguration(settings, settings.isShared());
      runManager.setSelectedConfiguration(settings);
      ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }
  }
}
