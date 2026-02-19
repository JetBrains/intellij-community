// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CompactFileWriter {
  public static void saveToFile(@NotNull Collection<RawRuntimeModuleDescriptor> originalDescriptors,
                                @Nullable String bootstrapModuleName,
                                int generatorVersion,
                                @NotNull Path outputFile) throws IOException {
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
      out.writeInt(CompactFileReader.FORMAT_VERSION);
      out.writeInt(generatorVersion);
      
      boolean hasBootstrap = bootstrapModuleName != null;
      out.writeBoolean(hasBootstrap);
      if (hasBootstrap) {
        out.writeUTF(bootstrapModuleName);
        Collection<String> bootstrapClasspath = CachedClasspathComputation.computeClasspath(originalDescriptors, RuntimeModuleId.module(bootstrapModuleName));
        out.writeInt(bootstrapClasspath.size());
        for (String path : bootstrapClasspath) {
          out.writeUTF(path);
        }
      }
      
      out.writeBoolean(false);//hasMainPluginModule

      List<RawRuntimeModuleDescriptor> descriptors = new ArrayList<>(originalDescriptors);
      Collections.sort(descriptors, Comparator.comparing(descriptor -> descriptor.getModuleId().getStringId()));
      Map<RuntimeModuleId, Integer> indexes = new HashMap<>(descriptors.size());
      for (int i = 0; i < descriptors.size(); i++) {
        indexes.put(descriptors.get(i).getModuleId(), i);
      }
      List<RuntimeModuleId> unresolvedDependencies = new ArrayList<>();
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        for (RuntimeModuleId dependency : descriptor.getDependencyIds()) {
          if (!indexes.containsKey(dependency)) {
            unresolvedDependencies.add(dependency);
            int nextId = indexes.size();
            indexes.put(dependency, nextId);
          }
        }
      }
      
      out.writeInt(descriptors.size());
      out.writeInt(unresolvedDependencies.size());
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        out.writeUTF(descriptor.getModuleId().getStringId());
      }
      
      for (RuntimeModuleId dependency : unresolvedDependencies) {
        out.writeUTF(dependency.getStringId());
      }
      
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        out.writeInt(descriptor.getDependencyIds().size());
        for (RuntimeModuleId dependency : descriptor.getDependencyIds()) {
          Integer index = indexes.get(dependency);
          if (index == null) {
            throw new AssertionError("Unknown dependency '" + dependency.getPresentableName() + "' in '" + descriptor.getModuleId().getPresentableName() + "'");
          }
          out.writeInt(index);
        }
        out.writeInt(descriptor.getResourcePaths().size());
        for (String path : descriptor.getResourcePaths()) {
          out.writeUTF(path);
        }
      }
    }
  }

  /**
   * @deprecated use {@link #saveToFile(Collection, String, int, Path)} instead; {@code mainPluginModuleId} isn't supported anymore
   */
  @Deprecated(forRemoval = true)
  public static void saveToFile(@NotNull Collection<RawRuntimeModuleDescriptor> originalDescriptors,
                                @Nullable String bootstrapModuleName, @Nullable String mainPluginModuleId,
                                int generatorVersion,
                                @NotNull Path outputFile) throws IOException {
    saveToFile(originalDescriptors, bootstrapModuleName, generatorVersion, outputFile);
  }
}
