// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.IncludedRuntimeModule;
import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.product.RuntimeModuleGroup;
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the main module group which contains modules which are always enabled: platform modules, implementation-detail plugins, and 
 * essential plugins.
 */
public final class MainRuntimeModuleGroup implements RuntimeModuleGroup {
  private final @NotNull List<RawIncludedRuntimeModule> myRawRootModules;
  private final ProductMode myProductMode;
  private final RuntimeModuleRepository myRepository;
  private volatile List<IncludedRuntimeModule> myIncludedModules;
  private volatile Map<@NotNull RuntimeModuleId, @NotNull List<@NotNull RuntimeModuleId>> myNotIncludedModules;

  public MainRuntimeModuleGroup(@NotNull List<RawIncludedRuntimeModule> rawRootModules, @NotNull ProductMode currentMode, 
                                @NotNull RuntimeModuleRepository repository) {
    myRawRootModules = rawRootModules;
    myProductMode = currentMode;
    myRepository = repository;
  }

  @Override
  public @NotNull Map<@NotNull RuntimeModuleId, @NotNull List<@NotNull RuntimeModuleId>> getNotLoadedModuleIds() {
    if (myNotIncludedModules == null) {
      updateIncludedAndNotIncludedModules();
    }
    return myNotIncludedModules;
  }

  @Override
  public @NotNull Set<@NotNull RuntimeModuleId> getOptionalModuleIds() {
    return myRawRootModules.stream()
      .filter(it -> it.getLoadingRule().equals(RuntimeModuleLoadingRule.OPTIONAL))
      .map(RawIncludedRuntimeModule::getModuleId)
      .collect(Collectors.toSet());
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      updateIncludedAndNotIncludedModules();
    }
    return myIncludedModules;
  }

  private void updateIncludedAndNotIncludedModules() {
    List<IncludedRuntimeModule> rootIncludedModules = new ArrayList<>();
    Map<@NotNull RuntimeModuleId, @NotNull List<@NotNull RuntimeModuleId>> rootNotIncludedModules = new HashMap<>();
    Set<RuntimeModuleDescriptor> rootModules = new HashSet<>();
    ProductModeMatcher matcher = new ProductModeMatcher(myProductMode);
    for (RawIncludedRuntimeModule rawRootModule : myRawRootModules) {
      IncludedRuntimeModule included = rawRootModule.resolve(myRepository);
      if (included != null && matcher.matches(included.getModuleDescriptor())) {
        rootIncludedModules.add(included);
        rootModules.add(included.getModuleDescriptor());
      }
      else {
        rootNotIncludedModules.put(rawRootModule.getModuleId(), List.of());
      }
    }

    List<IncludedRuntimeModule> result = new ArrayList<>(rootIncludedModules);
    Set<RuntimeModuleDescriptor> visited = new HashSet<>();
    for (IncludedRuntimeModule rootModule : rootIncludedModules) {
      collectDependencies(rootModule.getModuleDescriptor(), rootModules, visited, result);
    }

    this.myIncludedModules = result;
    this.myNotIncludedModules = rootNotIncludedModules;
  }

  private static void collectDependencies(RuntimeModuleDescriptor descriptor,
                                          Set<RuntimeModuleDescriptor> rootModules,
                                          Set<RuntimeModuleDescriptor> visited,
                                          List<IncludedRuntimeModule> result) {
    for (RuntimeModuleDescriptor dependency : descriptor.getDependencies()) {
      if (!visited.add(dependency)) continue;
      if (!rootModules.contains(dependency)) {
        result.add(new IncludedRuntimeModuleImpl(dependency, RuntimeModuleLoadingRule.ON_DEMAND));
      }
      collectDependencies(dependency, rootModules, visited, result);
    }
  }

  @Override
  public String toString() {
    return "MainRuntimeModuleGroup{rootModules=" + myRawRootModules + "}";
  }
}
