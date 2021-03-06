// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.impl.RunManagerImplKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationTypesRegistrar;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import org.jetbrains.annotations.NotNull;

public final class CloudRunConfigurationUtil {
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

    ConfigurationFactory configurationFactory = DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(account.getType()).getFactoryForType(deploymentSource.getType());
    final RunnerAndConfigurationSettings runSettings = runManager.createConfiguration(name, configurationFactory);
    @SuppressWarnings("unchecked")
    DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();
    result.setServerName(account.getName());
    result.setDeploymentSource(deploymentSource);
    result.setDeploymentConfiguration(deploymentConfiguration);
    RunManagerImplKt.callNewConfigurationCreated(configurationFactory, runSettings.getConfiguration());

    runManager.addConfiguration(runSettings);
    runManager.setSelectedConfiguration(runSettings);

    return result;
  }

  private static String generateRunConfigurationName(@NotNull RemoteServer<?> account, Module module) {
    String accountName = account.getName();
    String moduleName = module.getName();
    return CloudBundle.message("run.configuration.name", accountName, moduleName);
  }
}
