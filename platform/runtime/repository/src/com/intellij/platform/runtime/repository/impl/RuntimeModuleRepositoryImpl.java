// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeModuleRepositoryImpl implements RuntimeModuleRepository {
  private final Map<RuntimeModuleId, RuntimeModuleDescriptor> myModulesDescriptors;

  public RuntimeModuleRepositoryImpl(Map<RuntimeModuleId, RuntimeModuleDescriptor> modulesDescriptors) {
    myModulesDescriptors = modulesDescriptors;
  }

  @TestOnly
  public RuntimeModuleRepositoryImpl(@NotNull Map<String, RawRuntimeModuleDescriptor> descriptorMap, @NotNull Path basePath) {
    myModulesDescriptors = createDescriptors(descriptorMap, basePath);
  }

  @Nullable
  @Override
  public RuntimeModuleDescriptor findModule(@NotNull RuntimeModuleId moduleId) {
    return myModulesDescriptors.get(moduleId);
  }

  @NotNull
  public static Map<RuntimeModuleId, RuntimeModuleDescriptor> createDescriptors(@NotNull Map<String, RawRuntimeModuleDescriptor> rawData,
                                                                                @NotNull Path basePath) {
    Map<RuntimeModuleId, RuntimeModuleDescriptor> result = new HashMap<>(rawData.size());
    Map<RuntimeModuleId, ArrayList<RuntimeModuleDescriptor>> dependenciesMap = new HashMap<>(rawData.size());
    for (Map.Entry<String, RawRuntimeModuleDescriptor> entry : rawData.entrySet()) {
      ArrayList<RuntimeModuleDescriptor> dependencies = new ArrayList<>();
      RuntimeModuleId id = RuntimeModuleId.raw(entry.getKey());
      dependenciesMap.put(id, dependencies);
      List<String> resourcePaths = entry.getValue().getResourcePaths();
      result.put(id, new RuntimeModuleDescriptorImpl(id, basePath, resourcePaths, dependencies));
    }

    for (Map.Entry<RuntimeModuleId, RuntimeModuleDescriptor> entry : result.entrySet()) {
      RuntimeModuleId id = entry.getKey();
      List<RuntimeModuleDescriptor> dependencyList = dependenciesMap.get(id);
      RawRuntimeModuleDescriptor data = rawData.get(id.getStringId());
      for (String dependency : data.getDependencies()) {
        RuntimeModuleDescriptor depDescriptor = result.get(RuntimeModuleId.raw(dependency));
        if (depDescriptor == null) {
          throw new MalformedRepositoryException("Cannot find module '" + dependency + "' referenced from module '" + id.getStringId() + "'");
        }
        dependencyList.add(depDescriptor);
      }
    }
    return result;
  }

  @Override
  @NotNull
  public RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleId) {
    RuntimeModuleDescriptor dependency = findModule(moduleId);
    if (dependency == null) {
      throw new MalformedRepositoryException("Cannot find module '" + moduleId.getStringId() + "' in " + this);
    }
    return dependency;
  }
}
