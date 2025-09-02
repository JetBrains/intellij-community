// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class CachedClasspathComputation {

  static @NotNull Collection<String> computeClasspath(Collection<RawRuntimeModuleDescriptor> descriptors, String moduleName) {
    Set<String> classpath = new LinkedHashSet<>();
    Map<String, RawRuntimeModuleDescriptor> descriptorMap = new HashMap<>();
    for (RawRuntimeModuleDescriptor descriptor : descriptors) {
      descriptorMap.put(descriptor.getId(), descriptor);
    }
    collectClasspathEntries(moduleName, descriptorMap, new HashSet<>(), classpath);
    return classpath;
  }

  private static void collectClasspathEntries(String moduleName,
                                              Map<String, RawRuntimeModuleDescriptor> descriptorMap,
                                              Set<String> processedModules,
                                              Set<String> classpath) {
    if (!processedModules.add(moduleName)) return;
    RawRuntimeModuleDescriptor descriptor = descriptorMap.get(moduleName);
    if (descriptor == null) return;
    classpath.addAll(descriptor.getResourcePaths());
    for (String dependency : descriptor.getDependencies()) {
      collectClasspathEntries(dependency, descriptorMap, processedModules, classpath);
    }
  }
}
