// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.ModuleImportance;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Describes a group of modules corresponding to a plugin.
 */
public final class PluginModuleGroup implements RuntimeModuleGroup {
  private final RuntimeModuleDescriptor myMainModule;
  private volatile List<IncludedRuntimeModule> myIncludedModules;

  public PluginModuleGroup(@NotNull RuntimeModuleDescriptor mainModule) {
    myMainModule = mainModule;
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      myIncludedModules = computeIncludedModules();
    }
    return myIncludedModules;
  }

  private List<IncludedRuntimeModule> computeIncludedModules() {
    //todo load additional modules from plugin.xml
    return List.of(new IncludedRuntimeModuleImpl(myMainModule, ModuleImportance.FUNCTIONAL, Collections.emptySet()));
  }

  @Override
  public String toString() {
    return "PluginModuleGroup{mainModule=" + myMainModule.getModuleId().getStringId() + "}";
  }
}
