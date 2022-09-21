// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationTypesManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Experimental
public final class DeploymentConfigurationTypesManagerImpl extends DeploymentConfigurationTypesManager {

  private static final @NotNull Logger LOG = Logger.getInstance(DeploymentConfigurationTypesManagerImpl.class);

  public static @NotNull DeploymentConfigurationTypesManagerImpl getImplementationInstance() {
    return ((DeploymentConfigurationTypesManagerImpl)getInstance());
  }

  private final ConcurrentMap<ServerType<?>, DeployToServerConfigurationType<?>> myConfigurationTypes = new ConcurrentHashMap<>();

  private DeploymentConfigurationTypesManagerImpl() { }

  @Override
  public <C extends ServerConfiguration> @NotNull DeployToServerConfigurationType<C> getConfigurationType(@NotNull ServerType<C> serverType) {
    @SuppressWarnings("unchecked") DeployToServerConfigurationType<C> result =
      (DeployToServerConfigurationType<C>)myConfigurationTypes.get(serverType);
    LOG.assertTrue(result != null,
                   "Cannot find run configuration type for server: " + serverType.getId());
    return result;
  }

  @Override
  public void registerConfigurationType(@NotNull ServerType<?> serverType) {
    DeployToServerConfigurationType<?> configurationType = new DeployToServerConfigurationType<>(serverType);

    try {
      getConfigurationTypeEP().registerExtension(configurationType);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    myConfigurationTypes.put(serverType, configurationType);
  }

  @Override
  public void unregisterConfigurationType(@NotNull ServerType<?> serverType) {
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
}
