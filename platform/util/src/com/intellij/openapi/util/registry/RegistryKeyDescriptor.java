// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RegistryKeyDescriptor {
  private final @NotNull String myName;
  private final @NotNull String myDefaultValue;
  private final @NotNull String myDescription;
  private final boolean myRestartRequired;
  private final String myPluginId;

  RegistryKeyDescriptor(@NotNull String name, @NotNull String description, @NotNull String defaultValue,
                        boolean restartRequired, @Nullable String pluginId) {
    myName = name;
    myDefaultValue = defaultValue;
    myDescription = description;
    myRestartRequired = restartRequired;
    myPluginId = pluginId;
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

  @Nullable
  String getPluginId() {
    return myPluginId;
  }
}
