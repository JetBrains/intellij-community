// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.IncludedRuntimeModule;
import com.intellij.platform.runtime.product.PluginModuleGroup;
import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver;
import com.intellij.platform.runtime.product.serialization.impl.PluginXmlReader;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
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
  private volatile Map<RuntimeModuleId, List<RuntimeModuleId>> myNotLoadedModuleIds;

  public PluginModuleGroupImpl(@NotNull RuntimeModuleDescriptor mainModule, @NotNull ProductMode currentMode, @NotNull RuntimeModuleRepository repository,
                               @NotNull ResourceFileResolver resourceFileResolver) {
    myMainModule = mainModule;
    myCurrentMode = currentMode;
    myRepository = repository;
    myResourceFileResolver = resourceFileResolver;
  }

  @Override
  public @NotNull RuntimeModuleDescriptor getMainModule() {
    return myMainModule;
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      loadIncludedModules();
    }
    return myIncludedModules;
  }

  @Override
  public @NotNull Map<@NotNull RuntimeModuleId, @NotNull List<@NotNull RuntimeModuleId>> getNotLoadedModuleIds() {
    if (myNotLoadedModuleIds == null) {
      loadIncludedModules();
    }
    return myNotLoadedModuleIds;
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
      if (rawModule.getLoadingRule().equals(RuntimeModuleLoadingRule.OPTIONAL)) {
        optionalModuleIds.add(rawModule.getModuleId());
      }
    }
    myOptionalModuleIds = optionalModuleIds;
    myIncludedModules = includedModules;
    myNotLoadedModuleIds = matcher.getUnmatchedModules();
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
