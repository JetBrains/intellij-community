// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.remoteServer.ServerType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeployToServerConfigurationTypesRegistrar implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    //todo[nik] improve this: configuration types should be loaded lazily
    getConfigurationTypesExtPoint()
      .registerExtensions(ContainerUtil.map(ServerType.EP_NAME.getExtensionList(), type -> new DeployToServerConfigurationType(type)));

    ServerType.EP_NAME.addExtensionPointListener(
      new ExtensionPointListener<ServerType>() {
        @Override
        public void extensionAdded(@NotNull ServerType addedServer, @NotNull PluginDescriptor pluginDescriptor) {
          getConfigurationTypesExtPoint().registerExtension(new DeployToServerConfigurationType(addedServer));
        }

        @Override
        public void extensionRemoved(@NotNull ServerType removedServer, @NotNull PluginDescriptor pluginDescriptor) {
          DeployToServerConfigurationType deployForServer = findDeployConfigurationType(removedServer);
          if (deployForServer != null) {
            getConfigurationTypesExtPoint().unregisterExtension(deployForServer);
          }
        }
      }, null);
  }

  @NotNull
  public static DeployToServerConfigurationType getDeployConfigurationType(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType result = findDeployConfigurationType(serverType);
    if (result == null) {
      throw new IllegalArgumentException("Cannot find run configuration type for " + serverType.getClass());
    }
    return result;
  }

  @Nullable
  private static DeployToServerConfigurationType findDeployConfigurationType(@NotNull ServerType<?> serverType) {
    String serverTypeId = serverType.getId();
    return (DeployToServerConfigurationType)ConfigurationType.CONFIGURATION_TYPE_EP
      .findFirstSafe(next -> isDeployForServerType(next, serverTypeId));
  }

  private static ExtensionPointImpl<ConfigurationType> getConfigurationTypesExtPoint() {
    return (ExtensionPointImpl<ConfigurationType>)ConfigurationType.CONFIGURATION_TYPE_EP.getPoint();
  }

  private static boolean isDeployForServerType(@NotNull ConfigurationType configurationType, @NotNull String serverTypeId) {
    return configurationType instanceof DeployToServerConfigurationType &&
           ((DeployToServerConfigurationType)configurationType).isForServerType(serverTypeId);
  }
}
