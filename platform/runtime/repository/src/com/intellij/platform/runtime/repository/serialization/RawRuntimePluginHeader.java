// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the main data about a plugin which is needed to configure discover its modules without parsing the plugin descriptor.
 */
public final class RawRuntimePluginHeader {
  private final @NotNull String myPluginId;
  private final @NotNull RuntimeModuleId myPluginDescriptorModuleId;
  private final @NotNull List<RawIncludedRuntimeModule> myIncludedModules;

  private RawRuntimePluginHeader(@NotNull String pluginId,
                                 @NotNull RuntimeModuleId pluginDescriptorModuleId,
                                 @NotNull List<RawIncludedRuntimeModule> includedModules) {
    myPluginId = pluginId;
    myPluginDescriptorModuleId = pluginDescriptorModuleId;
    myIncludedModules = includedModules;
  }

  public @NotNull String getPluginId() {
    return myPluginId;
  }

  public @NotNull RuntimeModuleId getPluginDescriptorModuleId() {
    return myPluginDescriptorModuleId;
  }

  public @NotNull List<RawIncludedRuntimeModule> getIncludedModules() {
    return myIncludedModules;
  }

  @Override
  public String toString() {
    return "RawRuntimePluginHeader{" +
           "pluginId=" + myPluginId +
           ", pluginDescriptorModuleId=" + myPluginDescriptorModuleId +
           ", includedModules=" + myIncludedModules +
           '}';
  }

  public static RawRuntimePluginHeader create(@NotNull String pluginId,
                                              @NotNull RuntimeModuleId pluginDescriptorModuleId,
                                              @NotNull List<RawIncludedRuntimeModule> includedModules) {
    return new RawRuntimePluginHeader(pluginId, pluginDescriptorModuleId, includedModules);
  }
}
