// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class RuntimeModuleDescriptorImpl implements RuntimeModuleDescriptor {
  private final RuntimeModuleId myId;
  private final List<RuntimeModuleDescriptor> myDependencies;
  private final Path myBasePath;
  private final List<String> myResourcePaths;
  private volatile @Nullable List<ResourceRoot> myResourceRoots;

  RuntimeModuleDescriptorImpl(@NotNull RuntimeModuleId moduleId, @NotNull Path basePath, @NotNull List<String> resourcePaths,
                              @NotNull List<RuntimeModuleDescriptor> dependencies) {
    myId = moduleId;
    myBasePath = basePath;
    myResourcePaths = resourcePaths;
    myDependencies = dependencies;
  }

  @Override
  public @NotNull RuntimeModuleId getModuleId() {
    return myId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId.equals(((RuntimeModuleDescriptorImpl)o).myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public @NotNull List<RuntimeModuleDescriptor> getDependencies() {
    return myDependencies;
  }

  @Override
  public @NotNull List<Path> getResourceRootPaths() {
    List<Path> paths = new ArrayList<>();
    for (ResourceRoot root : resolveResourceRoots()) {
      paths.add(root.getRootPath());
    }
    return paths;
  }

  @Override
  public @Nullable InputStream readFile(@NotNull String relativePath) throws IOException {
    for (ResourceRoot root : resolveResourceRoots()) {
      InputStream inputStream = root.openFile(relativePath);
      if (inputStream != null) {
        return inputStream;
      }
    }
    return null;
  }

  private @NotNull List<? extends ResourceRoot> resolveResourceRoots() {
    List<ResourceRoot> resourceRoots = myResourceRoots;
    if (resourceRoots == null) {
      resourceRoots = new ArrayList<>(myResourcePaths.size());
      for (String path : myResourcePaths) {
        resourceRoots.add(createResourceRoot(myBasePath, path));
      }
      myResourceRoots = resourceRoots;
    }
    return resourceRoots;
  }

  @Override
  public @NotNull List<Path> getModuleClasspath() {
    Set<Path> classpath = new LinkedHashSet<>();
    collectDependencies(this, new LinkedHashSet<>(), classpath);
    return List.copyOf(classpath);
  }

  private static void collectDependencies(RuntimeModuleDescriptor module, Set<RuntimeModuleId> visited, Set<Path> classpath) {
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
      return ResourcePathMacros.resolve(relativePath, baseDir);
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

  @Override
  public String toString() {
    return "RuntimeModuleDescriptor{id=" + myId + '}';
  }
}
