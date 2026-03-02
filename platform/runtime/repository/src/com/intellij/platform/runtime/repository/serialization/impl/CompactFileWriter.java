// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CompactFileWriter {
  public static void saveToFile(@NotNull Collection<RawRuntimeModuleDescriptor> originalModuleDescriptors,
                                @NotNull Collection<RawRuntimePluginHeader> originalPluginHeaders,
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
        Collection<String> bootstrapClasspath = CachedClasspathComputation.computeClasspath(originalModuleDescriptors, RuntimeModuleId.module(bootstrapModuleName));
        out.writeInt(bootstrapClasspath.size());
        for (String path : bootstrapClasspath) {
          out.writeUTF(path);
        }
      }
      
      List<RawRuntimeModuleDescriptor> moduleDescriptors = new ArrayList<>(originalModuleDescriptors);
      Collections.sort(moduleDescriptors, Comparator.comparing(descriptor -> descriptor.getModuleId().getStringId()));
      List<RawRuntimePluginHeader> pluginHeaders = new ArrayList<>(originalPluginHeaders);
      Collections.sort(pluginHeaders, Comparator.comparing(RawRuntimePluginHeader::getPluginId));


      Map<RuntimeModuleId, Integer> moduleIdIndexes = new HashMap<>(moduleDescriptors.size());
      List<RuntimeModuleId> allModuleIds = new ArrayList<>(moduleDescriptors.size());
      for (int i = 0; i < moduleDescriptors.size(); i++) {
        RuntimeModuleId moduleId = moduleDescriptors.get(i).getModuleId();
        moduleIdIndexes.put(moduleId, i);
        allModuleIds.add(moduleId);
      }
      Set<RuntimeModuleId> referencedModuleIds = new LinkedHashSet<>();
      for (RawRuntimeModuleDescriptor descriptor : moduleDescriptors) {
        referencedModuleIds.addAll(descriptor.getDependencyIds());
      }
      for (RawRuntimePluginHeader pluginHeader : pluginHeaders) {
        referencedModuleIds.add(pluginHeader.getPluginDescriptorModuleId());
        for (RawIncludedRuntimeModule includedModule : pluginHeader.getIncludedModules()) {
          referencedModuleIds.add(includedModule.getModuleId());
          RuntimeModuleId requiredIfAvailableId = includedModule.getRequiredIfAvailableId();
          if (requiredIfAvailableId != null) {
            referencedModuleIds.add(requiredIfAvailableId);
          }
        }
      }
      for (RuntimeModuleId referencedModuleId : referencedModuleIds) {
        if (!moduleIdIndexes.containsKey(referencedModuleId)) {
          allModuleIds.add(referencedModuleId);
          int nextId = moduleIdIndexes.size();
          moduleIdIndexes.put(referencedModuleId, nextId);
        }
      }

      List<String> namespaces = new ArrayList<>();
      Map<String, Integer> namespaceIndexes = new HashMap<>();
      for (RuntimeModuleId moduleId : allModuleIds) {
        if (!namespaceIndexes.containsKey(moduleId.getNamespace())) {
          namespaces.add(moduleId.getNamespace());
          int nextId = namespaceIndexes.size();
          namespaceIndexes.put(moduleId.getNamespace(), nextId);
        }
      }
      out.writeInt(namespaces.size());
      for (String namespace : namespaces) {
        out.writeUTF(namespace);
      }

      out.writeInt(moduleDescriptors.size());
      out.writeInt(allModuleIds.size() - moduleDescriptors.size());

      for (RuntimeModuleId moduleId : allModuleIds) {
        out.writeInt(namespaceIndexes.get(moduleId.getNamespace()));
        out.writeUTF(moduleId.getStringId());
      }

      for (RawRuntimeModuleDescriptor descriptor : moduleDescriptors) {
        out.write(indexOf(descriptor.getVisibility(), CompactFileReader.VISIBILITIES_BY_INDEX));
        out.writeInt(descriptor.getDependencyIds().size());
        for (RuntimeModuleId dependency : descriptor.getDependencyIds()) {
          Integer index = moduleIdIndexes.get(dependency);
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

      out.writeInt(pluginHeaders.size());
      for (RawRuntimePluginHeader pluginHeader : pluginHeaders) {
        out.writeUTF(pluginHeader.getPluginId());
        Integer pluginDescriptorModuleIdIndex = moduleIdIndexes.get(pluginHeader.getPluginDescriptorModuleId());
        if (pluginDescriptorModuleIdIndex == null) {
          throw new AssertionError("Unknown plugin descriptor module '" + pluginHeader.getPluginDescriptorModuleId().getPresentableName() + "' in '" + pluginHeader.getPluginId() + "'");
        }
        out.writeInt(pluginDescriptorModuleIdIndex);
        out.writeInt(pluginHeader.getIncludedModules().size());
        for (RawIncludedRuntimeModule includedModule : pluginHeader.getIncludedModules()) {
          Integer includedModuleIndex = moduleIdIndexes.get(includedModule.getModuleId());
          if (includedModuleIndex == null) {
            throw new AssertionError("Unknown included module '" + includedModule.getModuleId().getPresentableName() + "' in '" + pluginHeader.getPluginId() + "'");
          }
          out.writeInt(includedModuleIndex);
          out.write(indexOf(includedModule.getLoadingRule(), CompactFileReader.LOADING_RULES_BY_INDEX));
          RuntimeModuleId requiredIfAvailableId = includedModule.getRequiredIfAvailableId();
          if (requiredIfAvailableId != null) {
            Integer requiredIfAvailableIndex = moduleIdIndexes.get(requiredIfAvailableId);
            if (requiredIfAvailableIndex == null) {
              throw new AssertionError("Unknown required-if-available module '" + requiredIfAvailableId.getPresentableName() + "' in '" + pluginHeader.getPluginId() + "'");
            }
            out.writeInt(requiredIfAvailableIndex);
          }
          else {
            out.writeInt(-1);
          }
        }
      }
    }
  }

  private static <T> int indexOf(@NotNull T item, @NotNull T @NotNull [] array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i].equals(item)) return i;
    }
    throw new AssertionError("Cannot find index of " + item + " in " + Arrays.toString(array));
  }

  /**
   * @deprecated use {@link #saveToFile(Collection, Collection, String, int, Path)} instead; {@code mainPluginModuleId} isn't supported anymore
   */
  @Deprecated(forRemoval = true)
  public static void saveToFile(@NotNull Collection<RawRuntimeModuleDescriptor> originalDescriptors,
                                @Nullable String bootstrapModuleName, @Nullable String mainPluginModuleId,
                                int generatorVersion,
                                @NotNull Path outputFile) throws IOException {
    saveToFile(originalDescriptors, Collections.emptyList(), bootstrapModuleName, generatorVersion, outputFile);
  }
}
