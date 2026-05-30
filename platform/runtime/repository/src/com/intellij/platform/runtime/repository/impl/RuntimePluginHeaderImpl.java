// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimePluginHeader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RuntimePluginHeaderImpl implements RuntimePluginHeader {
  private final @NotNull String myPluginId;
  private final @NotNull RuntimeModuleId myPluginDescriptorModuleId;
  private final @NotNull List<IncludedRuntimeModule> myIncludedModules;

  public RuntimePluginHeaderImpl(@NotNull String pluginId,
                                 @NotNull RuntimeModuleId pluginDescriptorModuleId,
                                 @NotNull List<IncludedRuntimeModule> includedModules) {
    myPluginId = pluginId;
    myPluginDescriptorModuleId = pluginDescriptorModuleId;
    myIncludedModules = includedModules;
  }

  @Override
  public @NotNull String getPluginId() {
    return myPluginId;
  }

  @Override
  public @NotNull RuntimeModuleId getPluginDescriptorModuleId() {
    return myPluginDescriptorModuleId;
  }

  @Override
  public @NotNull List<IncludedRuntimeModule> getIncludedModules() {
    return myIncludedModules;
  }

  @Override
  public String toString() {
    return "RuntimePluginHeader{" +
           "pluginId=" + myPluginId +
           ", pluginDescriptorModuleId=" + myPluginDescriptorModuleId +
           ", includedModules=" + myIncludedModules +
           '}';
  }
}
