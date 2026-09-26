// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleHeader;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RuntimeModuleHeaderImpl implements RuntimeModuleHeader {
  private @NotNull RuntimeModuleId myModuleId;
  private @NotNull List<@NotNull RuntimeModuleId> myDependencies;
  private final Path myBasePath;
  private final List<String> myResourcePaths;
  private volatile @Nullable List<ResourceRoot> myResourceRoots;

  RuntimeModuleHeaderImpl(@NotNull RuntimeModuleId moduleId,
                          @NotNull Path basePath,
                          @NotNull List<@NotNull String> resourcePaths,
                          @NotNull List<@NotNull RuntimeModuleId> dependencies) {
    myModuleId = moduleId;
    myDependencies = dependencies;
    myBasePath = basePath;
    myResourcePaths = resourcePaths;
  }

  @Override
  public @NotNull RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  @Override
  public @NotNull List<@NotNull RuntimeModuleId> getDependencies() {
    return myDependencies;
  }

  @Override
  public @NotNull List<@NotNull Path> getOwnClasspath() {
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
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    return myModuleId.equals(((RuntimeModuleHeaderImpl)o).myModuleId);
  }

  @Override
  public int hashCode() {
    return myModuleId.hashCode();
  }
}
