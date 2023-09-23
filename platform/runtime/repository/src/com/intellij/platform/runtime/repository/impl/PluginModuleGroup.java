// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.repository.serialization.impl.PluginXmlReader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Describes a group of modules corresponding to a plugin.
 */
public final class PluginModuleGroup implements RuntimeModuleGroup {
  private final RuntimeModuleDescriptor myMainModule;
  private final RuntimeModuleRepository myRepository;
  private volatile List<IncludedRuntimeModule> myIncludedModules;

  public PluginModuleGroup(@NotNull RuntimeModuleDescriptor mainModule, @NotNull RuntimeModuleRepository repository) {
    myMainModule = mainModule;
    myRepository = repository;
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      myIncludedModules = PluginXmlReader.loadPluginModules(myMainModule, myRepository);
    }
    return myIncludedModules;
  }

  @Override
  public String toString() {
    return "PluginModuleGroup{mainModule=" + myMainModule.getModuleId().getStringId() + "}";
  }
}
