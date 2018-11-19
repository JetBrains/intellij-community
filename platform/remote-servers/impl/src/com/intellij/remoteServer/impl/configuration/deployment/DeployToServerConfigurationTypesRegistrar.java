// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeployToServerConfigurationTypesRegistrar implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    //todo[nik] improve this: configuration types should be loaded lazily
    ExtensionPoint<ConfigurationType> point = ConfigurationType.CONFIGURATION_TYPE_EP.getPoint(null);
    for (ServerType serverType : ServerType.EP_NAME.getExtensionList()) {
      point.registerExtension(new DeployToServerConfigurationType(serverType));
    }
  }

  @NotNull
  public static DeployToServerConfigurationType getDeployConfigurationType(@NotNull ServerType<?> serverType) {
    for (ConfigurationType type : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList()) {
      if (type instanceof DeployToServerConfigurationType) {
        DeployToServerConfigurationType configurationType = (DeployToServerConfigurationType)type;
        if (configurationType.getServerType().equals(serverType)) {
          return configurationType;
        }
      }
    }
    throw new IllegalArgumentException("Cannot find run configuration type for " + serverType.getClass());
  }
}
