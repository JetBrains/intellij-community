// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.IncludedRuntimeModule;
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.product.RuntimeModuleGroup;
import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
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

  public MainRuntimeModuleGroup(@NotNull List<RawIncludedRuntimeModule> rawRootModules, @NotNull ProductMode currentMode, 
                                @NotNull RuntimeModuleRepository repository) {
    myRawRootModules = rawRootModules;
    myProductMode = currentMode;
    myRepository = repository;
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
      myIncludedModules = computeIncludedModules();
    }
    return myIncludedModules;
  }

  private List<IncludedRuntimeModule> computeIncludedModules() {
    List<IncludedRuntimeModule> rootIncludedModules = new ArrayList<>();
    Set<RuntimeModuleDescriptor> rootModules = new HashSet<>();
    ProductModeMatcher matcher = new ProductModeMatcher(myProductMode);
    for (RawIncludedRuntimeModule rawRootModule : myRawRootModules) {
      IncludedRuntimeModule included = rawRootModule.resolve(myRepository);
      if (included != null && matcher.matches(included.getModuleDescriptor())) {
        rootIncludedModules.add(included);
        rootModules.add(included.getModuleDescriptor());
      }
    }

    List<IncludedRuntimeModule> result = new ArrayList<>(rootIncludedModules);
    Set<RuntimeModuleDescriptor> visited = new HashSet<>();
    for (IncludedRuntimeModule rootModule : rootIncludedModules) {
      collectDependencies(rootModule.getModuleDescriptor(), rootModules, visited, result);
    }
    return result;
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
