// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.remoteServer.ServerType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeployToServerConfigurationTypesRegistrar implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    //todo[nik] improve this: configuration types should be loaded lazily
    ((ExtensionPointImpl<ConfigurationType>)ConfigurationType.CONFIGURATION_TYPE_EP.getPoint(null))
      .registerExtensions(ContainerUtil.map(ServerType.EP_NAME.getExtensionList(), type -> new DeployToServerConfigurationType(type)));
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
