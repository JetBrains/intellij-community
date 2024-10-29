// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.*;
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver;
import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.product.serialization.impl.PluginXmlReader;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Describes a group of modules corresponding to a plugin.
 */
public final class PluginModuleGroupImpl implements PluginModuleGroup {
  private final RuntimeModuleDescriptor myMainModule;
  private final ProductMode myCurrentMode;
  private final RuntimeModuleRepository myRepository;
  private final ResourceFileResolver myResourceFileResolver;
  private volatile List<IncludedRuntimeModule> myIncludedModules;
  private volatile Set<RuntimeModuleId> myOptionalModuleIds;

  public PluginModuleGroupImpl(@NotNull RuntimeModuleDescriptor mainModule, @NotNull ProductMode currentMode, @NotNull RuntimeModuleRepository repository,
                               @NotNull ResourceFileResolver resourceFileResolver) {
    myMainModule = mainModule;
    myCurrentMode = currentMode;
    myRepository = repository;
    myResourceFileResolver = resourceFileResolver;
  }

  @NotNull
  @Override
  public RuntimeModuleDescriptor getMainModule() {
    return myMainModule;
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      loadIncludedModules();
    }
    return myIncludedModules;
  }

  private void loadIncludedModules() {
    List<RawIncludedRuntimeModule> rawIncludedModules = PluginXmlReader.loadPluginModules(myMainModule, myRepository,
                                                                                          myResourceFileResolver);
    List<IncludedRuntimeModule> includedModules = new ArrayList<>();
    Set<RuntimeModuleId> optionalModuleIds = new LinkedHashSet<>();
    ProductModeMatcher matcher = new ProductModeMatcher(myCurrentMode);
    for (RawIncludedRuntimeModule rawModule : rawIncludedModules) {
      IncludedRuntimeModule included = rawModule.resolve(myRepository);
      if (included != null && matcher.matches(included.getModuleDescriptor())) {
        includedModules.add(included);
      }
      if (!rawModule.getLoadingRule().equals(RuntimeModuleLoadingRule.REQUIRED)) {
        optionalModuleIds.add(rawModule.getModuleId());
      }
    }
    myOptionalModuleIds = optionalModuleIds;
    myIncludedModules = includedModules;
  }

  @Override
  public @NotNull Set<@NotNull RuntimeModuleId> getOptionalModuleIds() {
    if (myOptionalModuleIds == null) {
      loadIncludedModules();
    }
    return myOptionalModuleIds;
  }

  @Override
  public String toString() {
    return "PluginModuleGroup{mainModule=" + myMainModule.getModuleId().getStringId() + "}";
  }
}
