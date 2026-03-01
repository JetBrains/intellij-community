// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class CachedClasspathComputation {

  static @NotNull Collection<String> computeClasspath(Collection<RawRuntimeModuleDescriptor> descriptors, RuntimeModuleId moduleName) {
    Set<String> classpath = new LinkedHashSet<>();
    Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptorMap = new HashMap<>();
    for (RawRuntimeModuleDescriptor descriptor : descriptors) {
      descriptorMap.put(descriptor.getModuleId(), descriptor);
    }
    collectClasspathEntries(moduleName, descriptorMap, new HashSet<>(), classpath);
    return classpath;
  }

  private static void collectClasspathEntries(RuntimeModuleId moduleId,
                                              Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptorMap,
                                              Set<RuntimeModuleId> processedModules,
                                              Set<String> classpath) {
    if (!processedModules.add(moduleId)) return;
    RawRuntimeModuleDescriptor descriptor = descriptorMap.get(moduleId);
    if (descriptor == null) return;
    classpath.addAll(descriptor.getResourcePaths());
    for (RuntimeModuleId dependency : descriptor.getDependencyIds()) {
      collectClasspathEntries(dependency, descriptorMap, processedModules, classpath);
    }
  }
}
