// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class RegistryKeyDescriptor {
  private final @NotNull String myName;
  private final @NotNull String myDefaultValue;
  private final @NotNull String myDescription;
  private final boolean myRestartRequired;
  private final boolean myOverrides;
  private final @Nullable String myPluginId;
  private final @Nullable String myPluginDescriptorPath;

  public RegistryKeyDescriptor(@NotNull String name,
                               @NotNull String description,
                               @NotNull String defaultValue,
                               boolean restartRequired,
                               boolean overrides,
                               @Nullable String pluginId,
                               @Nullable String pluginDescriptorPath) {
    myName = name;
    myDefaultValue = defaultValue;
    myDescription = description;
    myRestartRequired = restartRequired;
    myOverrides = overrides;
    myPluginId = pluginId;
    myPluginDescriptorPath = pluginDescriptorPath;
  }

  public @NotNull String getName() {
    return myName;
  }

  public @NotNull String getDescription() {
    return myDescription;
  }

  public @NotNull String getDefaultValue() {
    return myDefaultValue;
  }

  public boolean isRestartRequired() {
    return myRestartRequired;
  }

  public boolean isOverrides() {
    return myOverrides;
  }

  public @Nullable String getPluginId() {
    return myPluginId;
  }

  /**
   * Path to the descriptor file declaring this bean, relative to the plugin location.
   */
  public @Nullable String getPluginDescriptorPath() {
    return myPluginDescriptorPath;
  }
}
