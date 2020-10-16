// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;

public class DeployToServerRunConfigurationExtensionsManager
  extends RunConfigurationExtensionsManager<DeployToServerRunConfiguration<?, ?>, DeployToServerRunConfigurationExtension> {

  public DeployToServerRunConfigurationExtensionsManager() {
    super(DeployToServerRunConfigurationExtension.EP_NAME);
  }

  public static DeployToServerRunConfigurationExtensionsManager getInstance() {
    return ApplicationManager.getApplication().getService(DeployToServerRunConfigurationExtensionsManager.class);
  }

  public void patchDeploymentTask(@NotNull DeploymentTask<?> deploymentTask) {
    RunProfile runProfile = deploymentTask.getExecutionEnvironment().getRunProfile();
    if (runProfile instanceof DeployToServerRunConfiguration<?, ?>) {
      DeployToServerRunConfiguration<?, ?> runConfiguration = (DeployToServerRunConfiguration<?, ?>)runProfile;
      processApplicableExtensions(runConfiguration, next -> {
        next.patchDeploymentTask(runConfiguration, deploymentTask);
        return null;
      });
    }
  }
}
