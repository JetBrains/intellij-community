// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.openapi.components.ServiceManager;

public class DeployToServerRunConfigurationExtensionsManager
  extends RunConfigurationExtensionsManager<DeployToServerRunConfiguration<?, ?>, DeployToServerRunConfigurationExtension> {

  public DeployToServerRunConfigurationExtensionsManager() {
    super(DeployToServerRunConfigurationExtension.EP_NAME);
  }

  public static DeployToServerRunConfigurationExtensionsManager getInstance() {
    return ServiceManager.getService(DeployToServerRunConfigurationExtensionsManager.class);
  }
}
