// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.plugins;

import com.intellij.ide.plugins.PluginPermissionService;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ide.plugins.PluginPermissionJavaShim;
import com.intellij.openapi.updateSettings.DisablePluginRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Shows how Java code sends a request to {@link PluginPermissionService}
 * through {@link PluginPermissionJavaShim}.
 */
final class PluginPermissionServiceJavaDemo {
  private PluginPermissionServiceJavaDemo() {
  }

  @SuppressWarnings("HardCodedStringLiteral")
  static @NotNull CompletableFuture<String> requestDisable(@NotNull PluginId pluginId) {
    DisablePluginRequest request = new DisablePluginRequest(pluginId, "The Java demo asks to disable the plugin '" + pluginId.getIdString() + "'.");
    return PluginPermissionJavaShim.withPermission(request, action -> {
      action.apply();
      return "the operation started";
    });
  }
}
