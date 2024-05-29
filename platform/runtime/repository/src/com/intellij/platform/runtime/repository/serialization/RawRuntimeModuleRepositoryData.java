// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Describes raw data read from the module repository JAR. This class is used in code which parses the module repository, if you need to
 * get information about modules in IDE, use {@link com.intellij.platform.runtime.repository.RuntimeModuleRepository} instead.
 */
public final class RawRuntimeModuleRepositoryData {
  private final Map<String, RawRuntimeModuleDescriptor> myDescriptors;
  private final Path myBasePath;
  private final String myMainPluginModuleId;

  /**
   * Use {@link RuntimeModuleRepositorySerialization#loadFromJar(Path)} to create an instance in production code.
   */
  @ApiStatus.Internal
  public RawRuntimeModuleRepositoryData(@NotNull Map<String, RawRuntimeModuleDescriptor> descriptors, @NotNull Path basePath, @Nullable String mainPluginModuleId) {
    myDescriptors = descriptors;
    myBasePath = basePath;
    myMainPluginModuleId = mainPluginModuleId;
  }

  public @Nullable RawRuntimeModuleDescriptor findDescriptor(@NotNull String id) {
    return myDescriptors.get(id);
  }

  public @NotNull Path getBasePath() {
    return myBasePath;
  }
  
  public @NotNull Set<String> getAllIds() {
    return myDescriptors.keySet();
  }

  /**
   * Returns ID of the main plugin module for an additional module repository describing a custom plugin. 
   * For the main module repository (describing the IDE distribution) it returns {@code null}. 
   */
  public @Nullable String getMainPluginModuleId() {
    return myMainPluginModuleId;
  }
}
