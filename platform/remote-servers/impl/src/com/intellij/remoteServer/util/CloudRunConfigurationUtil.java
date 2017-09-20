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
package com.intellij.remoteServer.util;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationTypesRegistrar;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import org.jetbrains.annotations.NotNull;


public class CloudRunConfigurationUtil {

  public static <SC extends ServerConfiguration, DC extends DeploymentConfiguration>
  DeployToServerRunConfiguration<SC, DC> createRunConfiguration(RemoteServer<SC> account, Module module, DC deploymentConfiguration) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(module.getProject()).create(module);
    DeploymentSource deploymentSource = new ModuleDeploymentSourceImpl(modulePointer);
    return createRunConfiguration(account, module, deploymentSource, deploymentConfiguration);
  }

  public static <SC extends ServerConfiguration, DC extends DeploymentConfiguration>
  DeployToServerRunConfiguration<SC, DC> createRunConfiguration(@NotNull RemoteServer<SC> account,
                                                                @NotNull Module module,
                                                                @NotNull DeploymentSource deploymentSource,
                                                                DC deploymentConfiguration) {

    final RunManager runManager = RunManager.getInstance(module.getProject());
    String name = generateRunConfigurationName(account, module);

    final ConfigurationFactoryEx configurationFactory =
      DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(account.getType()).getFactoryForType(deploymentSource.getType());

    final RunnerAndConfigurationSettings runSettings = runManager.createRunConfiguration(name, configurationFactory);

    final DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();

    result.setServerName(account.getName());
    result.setDeploymentSource(deploymentSource);
    result.setDeploymentConfiguration(deploymentConfiguration);
    configurationFactory.onNewConfigurationCreated(runSettings.getConfiguration());

    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    return result;
  }

  private static String generateRunConfigurationName(@NotNull RemoteServer<?> account, Module module) {
    String accountName = account.getName();
    String moduleName = module.getName();
    return CloudBundle.getText("run.configuration.name", accountName, moduleName);
  }
}
