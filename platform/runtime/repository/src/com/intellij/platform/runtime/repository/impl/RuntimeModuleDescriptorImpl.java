// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class RuntimeModuleDescriptorImpl implements RuntimeModuleDescriptor {
  private final RuntimeModuleId myId;
  private final List<RuntimeModuleDescriptor> myDependencies;
  private final List<? extends ResourceRoot> myResourceRoots;

  RuntimeModuleDescriptorImpl(@NotNull RuntimeModuleId moduleId, @NotNull List<? extends ResourceRoot> roots, 
                              @NotNull List<RuntimeModuleDescriptor> dependencies) {
    myId = moduleId;
    myResourceRoots = roots;
    myDependencies = dependencies;
  }

  @NotNull
  @Override
  public RuntimeModuleId getModuleId() {
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

  @NotNull
  @Override
  public List<RuntimeModuleDescriptor> getDependencies() {
    return myDependencies;
  }

  @Override
  public @NotNull List<Path> getResourceRootPaths() {
    List<Path> paths = new ArrayList<>();
    for (ResourceRoot root : myResourceRoots) {
      paths.add(root.getRootPath());
    }
    return paths;
  }

  @Nullable
  @Override
  public InputStream readFile(@NotNull String relativePath) throws IOException {
    for (ResourceRoot root : myResourceRoots) {
      InputStream inputStream = root.openFile(relativePath);
      if (inputStream != null) {
        return inputStream;
      }
    }
    return null;
  }
}
