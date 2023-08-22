// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the main module group which contains modules which are always enabled: platform modules, implementation-detail plugins, and 
 * essential plugins.
 */
public final class MainRuntimeModuleGroup implements RuntimeModuleGroup {
  private final List<IncludedRuntimeModule> myRootModules;
  private volatile List<IncludedRuntimeModule> myIncludedModules;

  public MainRuntimeModuleGroup(@NotNull List<IncludedRuntimeModule> rootModules) {
    myRootModules = rootModules;
  }

  @Override
  public @NotNull List<@NotNull IncludedRuntimeModule> getIncludedModules() {
    if (myIncludedModules == null) {
      myIncludedModules = computeIncludedModules();
    }
    return myIncludedModules;
  }

  private List<IncludedRuntimeModule> computeIncludedModules() {
    List<IncludedRuntimeModule> result = new ArrayList<>(myRootModules);
    Set<RuntimeModuleDescriptor> rootModules = myRootModules.stream().map(IncludedRuntimeModule::getModuleDescriptor).collect(Collectors.toSet());
    Set<RuntimeModuleDescriptor> visited = new HashSet<>();
    for (IncludedRuntimeModule rootModule : myRootModules) {
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
        result.add(new IncludedRuntimeModuleImpl(dependency, ModuleImportance.SERVICE, Collections.emptySet()));
      }
      collectDependencies(dependency, rootModules, visited, result);
    }
  }

  @Override
  public String toString() {
    return "MainRuntimeModuleGroup{rootModules=" + myRootModules + "}";
  }
}
