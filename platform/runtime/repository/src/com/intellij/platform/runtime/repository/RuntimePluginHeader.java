// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/// Represents the main data about a plugin which is needed to discover its modules without parsing the plugin descriptor.
public interface RuntimePluginHeader {
  /// Returns the ID of the plugin as specified in the plugin descriptor.
  @NotNull String getPluginId();

  /// Returns the ID of the module that contains the plugin descriptor.
  @NotNull RuntimeModuleId getPluginDescriptorModuleId();

  /// Returns the list of modules included in the plugin: plugin content modules, JPS modules that aren't registered as content modules,
  /// and JPS project-level libraries included in the plugin's distribution.
  @NotNull List<IncludedRuntimeModule> getIncludedModules();
}
