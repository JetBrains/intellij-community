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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
      List<ResourceRoot> resourceRoots = new ArrayList<>(resourcePaths.size());
      for (String path : resourcePaths) {
        resourceRoots.add(createResourceRoot(basePath, path));
      }
      result.put(id, new RuntimeModuleDescriptorImpl(id, resourceRoots, dependencies));
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

  @Override
  public @NotNull List<Path> getModuleClasspath(@NotNull RuntimeModuleId moduleId) {
    List<Path> classpath = new ArrayList<>();
    collectDependencies(getModule(moduleId), new LinkedHashSet<>(), classpath);
    return classpath;
  }

  private static void collectDependencies(RuntimeModuleDescriptor module, Set<RuntimeModuleId> visited, List<Path> classpath) {
    if (visited.add(module.getModuleId())) {
      classpath.addAll(module.getResourceRootPaths());
      for (RuntimeModuleDescriptor dep : module.getDependencies()) {
        collectDependencies(dep, visited, classpath);
      }
    }
  }

  private static ResourceRoot createResourceRoot(Path baseDir, String relativePath) {
    Path root = convertToAbsolute(baseDir, relativePath);
    if (Files.isRegularFile(root)) {
      return new JarResourceRoot(root);
    }
    return new DirectoryResourceRoot(root);
  }

  private static Path convertToAbsolute(Path baseDir, String relativePath) {
    if (relativePath.startsWith("$")) {
      return ResourcePathMacros.resolve(relativePath);
    }
    Path root = baseDir;
    while (relativePath.startsWith("../")) {
      relativePath = relativePath.substring(3);
      root = root.getParent();
    }
    if (!relativePath.isEmpty()) {
      root = root.resolve(relativePath);
    }
    return root;
  }
}
