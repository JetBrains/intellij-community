// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The EP allows plugins to provide custom plugin repository URLs.
 * These URLs are not stored in IDE settings, so when a plugin which provided a custom repository is disabled,
 * any plugins installed from that repository cannot be updated anymore (unless they are available, say, in the Marketplace).
 */
public interface UpdateSettingsProvider {
  @ApiStatus.Internal
  ExtensionPointName<UpdateSettingsProvider> EP_NAME = new ExtensionPointName<>("com.intellij.updateSettingsProvider");

  @NotNull List<String> getPluginRepositories();

  @ApiStatus.Internal
  static @NotNull List<String> getRepositoriesFromProviders() {
    var result = new ArrayList<String>();
    EP_NAME.forEachExtensionSafe(provider -> result.addAll(provider.getPluginRepositories()));
    return result;
  }
}
