// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.NlsActions;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DeploymentConfigurator<D extends DeploymentConfiguration, S extends ServerConfiguration> {
  public abstract @NotNull List<DeploymentSource> getAvailableDeploymentSources();

  public abstract @NotNull D createDefaultConfiguration(@NotNull DeploymentSource source);

  public abstract @Nullable SettingsEditor<D> createEditor(@NotNull DeploymentSource source, @Nullable RemoteServer<S> server);

  /**
   * @see LocatableConfiguration#isGeneratedName()
   */
  public boolean isGeneratedConfigurationName(@NotNull String name,
                                              @NotNull DeploymentSource deploymentSource,
                                              @NotNull D deploymentConfiguration) {
    return false;
  }

  /**
   * @see LocatableConfiguration#suggestedName()
   */
  public @Nullable @NlsActions.ActionText String suggestConfigurationName(@NotNull DeploymentSource deploymentSource, @NotNull D deploymentConfiguration) {
    return null;
  }
}
