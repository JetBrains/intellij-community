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

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationType;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;


public class CloudRunConfigurationUtil {

  public static <SC extends ServerConfiguration, DC extends DeploymentConfiguration>
  DeployToServerRunConfiguration<SC, DC> createRunConfiguration(RemoteServer<SC> account, Module module, DC deploymentConfiguration) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(module.getProject()).create(module);
    DeploymentSource deploymentSource = new ModuleDeploymentSourceImpl(modulePointer);
    return createRunConfiguration(account, module, deploymentSource, deploymentConfiguration);
  }

  public static <SC extends ServerConfiguration, DC extends DeploymentConfiguration>
  DeployToServerRunConfiguration<SC, DC> createRunConfiguration(RemoteServer<SC> account,
                                                                Module module,
                                                                DeploymentSource deploymentSource,
                                                                DC deploymentConfiguration) {
    Project project = module.getProject();

    String accountName = account.getName();

    String name = generateRunConfigurationName(accountName, module.getName());

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    ConfigurationFactory configurationFactory = getRunConfigurationType(account.getType()).getConfigurationFactories()[0];
    final RunnerAndConfigurationSettings runSettings = runManager.createRunConfiguration(name, configurationFactory);

    final DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();

    result.setServerName(accountName);

    result.setDeploymentSource(deploymentSource);

    result.setDeploymentConfiguration(deploymentConfiguration);

    ((ConfigurationFactoryEx)configurationFactory).onNewConfigurationCreated(runSettings.getConfiguration());

    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    return result;
  }

  private static DeployToServerConfigurationType getRunConfigurationType(ServerType<?> cloudType) {
    String id = DeployToServerConfigurationType.getId(cloudType);
    for (ConfigurationType configurationType : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions()) {
      if (configurationType instanceof DeployToServerConfigurationType) {
        DeployToServerConfigurationType deployConfigurationType = (DeployToServerConfigurationType)configurationType;
        if (deployConfigurationType.getId().equals(id)) {
          return deployConfigurationType;
        }
      }
    }
    return null;
  }

  private static String generateRunConfigurationName(String serverName, String moduleName) {
    return CloudBundle.getText("run.configuration.name", serverName, moduleName);
  }
}
