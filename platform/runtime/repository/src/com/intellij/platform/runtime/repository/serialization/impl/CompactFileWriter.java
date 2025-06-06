// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CompactFileWriter {
  public static void saveToFile(@NotNull Collection<RawRuntimeModuleDescriptor> originalDescriptors,
                                @Nullable String bootstrapModuleName, @Nullable String mainPluginModuleId,
                                int generatorVersion,
                                @NotNull Path outputFile) throws IOException {
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
      out.writeInt(CompactFileReader.FORMAT_VERSION);
      out.writeInt(generatorVersion);
      
      boolean hasBootstrap = bootstrapModuleName != null;
      out.writeBoolean(hasBootstrap);
      if (hasBootstrap) {
        out.writeUTF(bootstrapModuleName);
        Collection<String> bootstrapClasspath = CachedClasspathComputation.computeClasspath(originalDescriptors, bootstrapModuleName);
        out.writeInt(bootstrapClasspath.size());
        for (String path : bootstrapClasspath) {
          out.writeUTF(path);
        }
      }
      
      boolean hasMainPluginModule = mainPluginModuleId != null;
      out.writeBoolean(hasMainPluginModule);
      if (hasMainPluginModule) {
        out.writeUTF(mainPluginModuleId);
      }
      
      List<RawRuntimeModuleDescriptor> descriptors = new ArrayList<>(originalDescriptors);
      Collections.sort(descriptors, Comparator.comparing(RawRuntimeModuleDescriptor::getId));
      Map<String, Integer> indexes = new HashMap<>(descriptors.size());
      for (int i = 0; i < descriptors.size(); i++) {
        indexes.put(descriptors.get(i).getId(), i);
      }
      List<String> unresolvedDependencies = new ArrayList<>();
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        for (String dependency : descriptor.getDependencies()) {
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
        out.writeUTF(descriptor.getId());
      }
      
      for (String dependency : unresolvedDependencies) {
        out.writeUTF(dependency);
      }
      
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        out.writeInt(descriptor.getDependencies().size());
        for (String dependency : descriptor.getDependencies()) {
          Integer index = indexes.get(dependency);
          if (index == null) {
            throw new AssertionError("Unknown dependency '" + dependency + "' in '" + descriptor.getId() + "'");
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
}
