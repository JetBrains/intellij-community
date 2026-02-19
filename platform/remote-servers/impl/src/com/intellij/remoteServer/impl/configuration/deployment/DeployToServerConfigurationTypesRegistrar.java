// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.SyntheticConfigurationTypeProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public final class DeployToServerConfigurationTypesRegistrar implements Disposable {

  private static final @NotNull Logger LOG = Logger.getInstance(DeployToServerConfigurationTypesRegistrar.class);

  public static @NotNull DeployToServerConfigurationTypesRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(DeployToServerConfigurationTypesRegistrar.class);
  }

  /**
   * @deprecated Please use {@link #getConfigurationType(ServerType)} directly.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull DeployToServerConfigurationType<?> getDeployConfigurationType(@NotNull ServerType<?> serverType) {
    return getInstance().getConfigurationType(serverType);
  }

  private final ConcurrentMap<ServerType<?>, DeployToServerConfigurationType<?>> myConfigurationTypes = new ConcurrentHashMap<>();

  private DeployToServerConfigurationTypesRegistrar() {
    ServerType.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {

      @Override
      public void extensionAdded(@NotNull ServerType<?> serverType,
                                 @NotNull PluginDescriptor pluginDescriptor) {
        registerConfigurationType(serverType);
      }

      @Override
      public void extensionRemoved(@NotNull ServerType<?> serverType,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        unregisterConfigurationType(serverType);
      }
    }, true, this);
  }

  public <C extends ServerConfiguration> @NotNull DeployToServerConfigurationType<C> getConfigurationType(@NotNull ServerType<C> serverType) {
    @SuppressWarnings("unchecked") DeployToServerConfigurationType<C> result =
      (DeployToServerConfigurationType<C>)myConfigurationTypes.get(serverType);
    LOG.assertTrue(result != null,
                   "Cannot find run configuration type for server: " + serverType.getId());
    return result;
  }

  private void registerConfigurationType(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType<?> configurationType = new DeployToServerConfigurationType<>(serverType);

    try {
      getConfigurationTypeEP().registerExtension(configurationType);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    myConfigurationTypes.put(serverType, configurationType);
  }

  private void unregisterConfigurationType(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType<?> configurationType = myConfigurationTypes.remove(serverType);
    LOG.assertTrue(configurationType != null,
                   "Run configuration has not been registered for server: " + serverType.getId());

    try {
      getConfigurationTypeEP().unregisterExtension(configurationType);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void dispose() {
    myConfigurationTypes.clear();
  }

  private static @NotNull ExtensionPointImpl<@NotNull ConfigurationType> getConfigurationTypeEP() {
    return (ExtensionPointImpl<@NotNull ConfigurationType>)ConfigurationType.CONFIGURATION_TYPE_EP.getPoint();
  }

  static final class Provider implements SyntheticConfigurationTypeProvider {

    @Override
    public @NotNull Collection<? extends DeployToServerConfigurationType<?>> getConfigurationTypes() {
      return getInstance().myConfigurationTypes.values();
    }
  }
}
