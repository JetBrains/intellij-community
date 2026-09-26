// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class RuntimeModuleDescriptorImpl implements RuntimeModuleDescriptor {
  private final RuntimeModuleHeaderImpl myHeader;
  private final List<RuntimeModuleDescriptor> myDependencies;

  RuntimeModuleDescriptorImpl(@NotNull RuntimeModuleHeaderImpl header, @NotNull List<RuntimeModuleDescriptor> dependencies) {
    myHeader = header;
    myDependencies = dependencies;
  }

  @Override
  public @NotNull RuntimeModuleId getModuleId() {
    return myHeader.getModuleId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myHeader.equals(((RuntimeModuleDescriptorImpl)o).myHeader);
  }

  @Override
  public int hashCode() {
    return myHeader.hashCode();
  }

  @Override
  public @NotNull List<RuntimeModuleDescriptor> getDependencies() {
    return myDependencies;
  }

  @Override
  public @NotNull List<Path> getResourceRootPaths() {
    return myHeader.getOwnClasspath();
  }

  @Override
  public @Nullable InputStream readFile(@NotNull String relativePath) throws IOException {
    return myHeader.readFile(relativePath);
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

  @Override
  public String toString() {
    return "RuntimeModuleDescriptor{id=" + getModuleId() + '}';
  }
}
