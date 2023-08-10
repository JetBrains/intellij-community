// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents the set of available modules. 
 */
@ApiStatus.NonExtendable
public interface RuntimeModuleRepository {
  /**
   * Creates a repository from a JAR file containing module descriptors.
   */
  static @NotNull RuntimeModuleRepository create(@NotNull Path moduleDescriptorsJarPath) throws MalformedRepositoryException {
    return new RuntimeModuleRepositoryImpl(moduleDescriptorsJarPath);
  }

  /**
   * Returns the module by the given {@code moduleId} or throws an exception if this module or any module from its dependencies is not 
   * found in the repository.
   */
  @NotNull RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleId);

  /**
   * Tries to resolve the module by the given {@code moduleId} and returns the resolution result. 
   */
  @NotNull ResolveResult resolveModule(@NotNull RuntimeModuleId moduleId);

  /**
   * Computes resource paths of a module with the given {@code moduleId} without resolving its dependencies.
   */
  @NotNull List<Path> getModuleResourcePaths(@NotNull RuntimeModuleId moduleId);
  
  interface ResolveResult {
    /**
     * Returns the module descriptor if resolution succeeded or {@code null} if it failed.
     */
    @Nullable RuntimeModuleDescriptor getResolvedModule();

    /**
     * Returns the path of transitive dependencies from the initially requested module to the module which failed to load if resolution
     * failed or an empty list of resolution succeeded.
     */
    @NotNull List<RuntimeModuleId> getFailedDependencyPath();
  }

  /**
   * Returns the classpath for the bootstrap module {@code bootstrapModuleName}.
   * This works faster than calculating classpath via {@link RuntimeModuleDescriptor#getModuleClasspath()} if the classpath for this 
   * bootstrap module is cached in MANIFEST.MF, because in that case it isn't needed to read and parse module descriptors.
   */
  @NotNull List<@NotNull Path> getBootstrapClasspath(@NotNull String bootstrapModuleName);
}
