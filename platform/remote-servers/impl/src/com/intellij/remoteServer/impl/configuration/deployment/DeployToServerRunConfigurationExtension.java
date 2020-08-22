// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;

public abstract class DeployToServerRunConfigurationExtension extends RunConfigurationExtensionBase<DeployToServerRunConfiguration<?, ?>> {
  public static final ExtensionPointName<DeployToServerRunConfigurationExtension> EP_NAME
    = new ExtensionPointName<>("com.intellij.remoteServer.runConfigurationExtension");

  protected void patchDeploymentTask(@NotNull DeployToServerRunConfiguration<?, ?> runConfiguration,
                                     @NotNull DeploymentTask<?> deploymentTask) {
    //
  }
}
