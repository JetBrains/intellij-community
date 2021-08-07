// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// sync preloaded service and not as an ApplicationInitializedListener because it takes relatively a lot of time
public final class DeployToServerConfigurationTypesRegistrar {
  private DeployToServerConfigurationTypesRegistrar() {
    //todo[nik] improve this: configuration types should be loaded lazily
    Collection<? extends ServerType<?>> collection = ServerType.EP_NAME.getExtensionList();
    List<DeployToServerConfigurationType> list = new ArrayList<>(collection.size());
    for (ServerType<?> t : collection) {
      list.add(new DeployToServerConfigurationType(t));
    }
    getConfigurationTypesExtPoint().registerExtensions(list);

    ServerType.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
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

  public static @NotNull DeployToServerConfigurationType getDeployConfigurationType(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType result = findDeployConfigurationType(serverType);
    if (result == null) {
      throw new IllegalArgumentException("Cannot find run configuration type for " + serverType.getClass());
    }
    return result;
  }

  private static @Nullable DeployToServerConfigurationType findDeployConfigurationType(@NotNull ServerType<?> serverType) {
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
