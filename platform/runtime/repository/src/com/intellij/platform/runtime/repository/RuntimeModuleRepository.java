// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Represents the set of available modules. 
 */
@ApiStatus.NonExtendable
public interface RuntimeModuleRepository {
  /**
   * Creates a repository from a JAR file containing module descriptors.
   */
  static @NotNull RuntimeModuleRepository create(@NotNull Path moduleDescriptorsJarPath) throws MalformedRepositoryException {
    Map<String, RawRuntimeModuleDescriptor> map = RuntimeModuleRepositorySerialization.loadFromJar(moduleDescriptorsJarPath);
    return new RuntimeModuleRepositoryImpl(RuntimeModuleRepositoryImpl.createDescriptors(map, moduleDescriptorsJarPath));
  }

  /**
   * Returns the module by the given {@code moduleId} or {@code null} if there is no module with such ID in the repository. 
   */
  @Nullable RuntimeModuleDescriptor findModule(@NotNull RuntimeModuleId moduleId);

  /**
   * Returns the module by the given {@code moduleId} or throws an exception if there is no module with such ID in the repository.
   */
  @NotNull RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleId);

  /**
   * Returns list of directories and JAR files containing classes of {@code moduleId} and its direct and transitive dependencies.
   */
  @NotNull List<Path> getModuleClasspath(@NotNull RuntimeModuleId moduleId);
}
