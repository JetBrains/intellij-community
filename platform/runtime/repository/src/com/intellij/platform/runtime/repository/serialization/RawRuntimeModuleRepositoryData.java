// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes raw data read from the module repository JAR. This class is used in code which parses the module repository, if you need to
 * get information about modules in IDE, use {@link com.intellij.platform.runtime.repository.RuntimeModuleRepository} instead.
 */
public final class RawRuntimeModuleRepositoryData {
  private final Map<RuntimeModuleId, RawRuntimeModuleDescriptor> myModuleDescriptors;
  private final List<RawRuntimePluginHeader> myPluginHeaders;
  private final Path myBasePath;

  private RawRuntimeModuleRepositoryData(@NotNull Map<RuntimeModuleId, RawRuntimeModuleDescriptor> moduleDescriptors,
                                         @NotNull List<RawRuntimePluginHeader> pluginHeaders,
                                         @NotNull Path basePath) {
    myModuleDescriptors = moduleDescriptors;
    myPluginHeaders = pluginHeaders;
    myBasePath = basePath;
  }

  /**
   * @deprecated use {@link RuntimeModuleRepositorySerialization#loadFromJar(Path)} or {@link #create(Map, Path)} instead
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public RawRuntimeModuleRepositoryData(@NotNull Map<String, RawRuntimeModuleDescriptor> moduleDescriptors, @NotNull Path basePath, @Nullable String mainPluginModuleId) {
    myModuleDescriptors = new LinkedHashMap<>(moduleDescriptors.size());
    for (Map.Entry<String, RawRuntimeModuleDescriptor> entry : moduleDescriptors.entrySet()) {
      myModuleDescriptors.put(RuntimeModuleId.raw(entry.getKey()), entry.getValue());
    }
    myPluginHeaders = Collections.emptyList();
    myBasePath = basePath;
  }

  public @Nullable RawRuntimeModuleDescriptor findDescriptor(@NotNull RuntimeModuleId id) {
    return myModuleDescriptors.get(id);
  }

  /**
   * @deprecated use {@link #findDescriptor(RuntimeModuleId)} instead
   */
  @Deprecated(forRemoval = true)
  public @Nullable RawRuntimeModuleDescriptor findDescriptor(@NotNull String id) {
    return myModuleDescriptors.get(RuntimeModuleId.raw(id));
  }

  public @NotNull Path getBasePath() {
    return myBasePath;
  }

  public @NotNull Set<RuntimeModuleId> getAllModuleIds() {
    return myModuleDescriptors.keySet();
  }

  public @NotNull List<@NotNull RawRuntimePluginHeader> getPluginHeaders() {
    return myPluginHeaders;
  }

  /**
   * @deprecated use {@link #getAllModuleIds()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Set<String> getAllIds() {
    return myModuleDescriptors.keySet().stream().map(RuntimeModuleId::getStringId).collect(Collectors.toSet());
  }

  /**
   * Returns ID of the main plugin module for an additional module repository describing a custom plugin. 
   * For the main module repository (describing the IDE distribution) it returns {@code null}.
   * @deprecated isn't supported anymore, always returns {@code null}
   */
  @Deprecated(forRemoval = true)
  public @Nullable String getMainPluginModuleId() {
    return null;
  }

  /**
   * Use {@link RuntimeModuleRepositorySerialization#loadFromCompactFile} to create an instance in production code.
   */
  @ApiStatus.Internal
  public static @NotNull RawRuntimeModuleRepositoryData create(
    @NotNull Map<RuntimeModuleId, RawRuntimeModuleDescriptor> moduleDescriptors,
    @NotNull List<RawRuntimePluginHeader> pluginHeaders,
    @NotNull Path basePath
  ) {
    return new RawRuntimeModuleRepositoryData(moduleDescriptors, pluginHeaders, basePath);
  }
}
