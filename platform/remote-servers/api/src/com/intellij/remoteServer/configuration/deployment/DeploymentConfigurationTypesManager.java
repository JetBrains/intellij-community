// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public abstract class DeploymentConfigurationTypesManager implements Disposable {

  public static @NotNull DeploymentConfigurationTypesManager getInstance() {
    return ApplicationManager.getApplication().getService(DeploymentConfigurationTypesManager.class);
  }

  public abstract <C extends ServerConfiguration> @NotNull ConfigurationType getConfigurationType(@NotNull ServerType<C> serverType);

  public abstract void registerConfigurationType(@NotNull ServerType<?> serverType);

  public abstract void unregisterConfigurationType(@NotNull ServerType<?> serverType);
}
