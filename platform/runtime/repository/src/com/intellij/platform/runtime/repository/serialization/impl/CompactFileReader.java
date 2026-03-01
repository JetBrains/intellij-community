// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility;
import com.intellij.platform.runtime.repository.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CompactFileReader {
  public static final int FORMAT_VERSION = 3;
  /** Enum elements are specified here explicitly to ensure that the format won't depend on order in the source file. */
  static final RuntimeModuleLoadingRule[] LOADING_RULES_BY_INDEX = {
    RuntimeModuleLoadingRule.EMBEDDED,
    RuntimeModuleLoadingRule.REQUIRED,
    RuntimeModuleLoadingRule.OPTIONAL,
    RuntimeModuleLoadingRule.ON_DEMAND,
  };
  /** Enum elements are specified here explicitly to ensure that the format won't depend on order in the source file. */
  static final RuntimeModuleVisibility[] VISIBILITIES_BY_INDEX = {
    RuntimeModuleVisibility.PRIVATE,
    RuntimeModuleVisibility.INTERNAL,
    RuntimeModuleVisibility.PUBLIC,
  };

  public static RawRuntimeModuleRepositoryData loadFromFile(@NotNull Path filePath) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(filePath)))) {
      int formatVersion = in.readInt();
      if (formatVersion == 2) {
        return CompactFileReaderForVersion2.loadFromInputStream(filePath, in);
      }
      if (formatVersion != FORMAT_VERSION) {
        throw new MalformedRepositoryException("'" + filePath + "' has unsupported format '" + formatVersion + "'");
      }
      skipGeneratorVersionAndBootstrapClasspath(in);

      Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptors = new HashMap<>();

      int namespacesCount = in.readInt();
      String[] namespaces = new String[namespacesCount];
      for (int i = 0; i < namespacesCount; i++) {
        namespaces[i] = in.readUTF();
      }

      int moduleDescriptorsCount = in.readInt();
      int unresolvedModuleIdCount = in.readInt();
      int totalModuleIdCount = moduleDescriptorsCount + unresolvedModuleIdCount;
      RuntimeModuleId[] moduleIds = new RuntimeModuleId[totalModuleIdCount];
      for (int i = 0; i < totalModuleIdCount; i++) {
        int namespaceIndex = in.readInt();
        String moduleName = in.readUTF();
        if (namespaceIndex < 0 || namespaceIndex >= namespacesCount) {
          throw new MalformedRepositoryException("Invalid namespace index '" + namespaceIndex + "' for '" + moduleName + "'");
        }
        moduleIds[i] = RuntimeModuleId.raw(moduleName, namespaces[namespaceIndex]);
      }

      for (int i = 0; i < moduleDescriptorsCount; i++) {
        RuntimeModuleId descriptorId = moduleIds[i];

        int visibilityIndex = in.read();
        if (visibilityIndex < 0 || visibilityIndex >= VISIBILITIES_BY_INDEX.length) {
          throw new MalformedRepositoryException("Invalid visibility index '" + visibilityIndex + "' in '" + descriptorId.getPresentableName() + "'");
        }
        RuntimeModuleVisibility visibility = VISIBILITIES_BY_INDEX[visibilityIndex];

        int dependenciesCount = in.readInt();
        List<RuntimeModuleId> dependencies = new ArrayList<>(dependenciesCount);
        for (int j = 0; j < dependenciesCount; j++) {
          int dependencyIndex = in.readInt();
          if (dependencyIndex < 0 || dependencyIndex >= totalModuleIdCount) {
            throw new MalformedRepositoryException("Invalid dependency index '" + dependencyIndex + "' in '" + descriptorId.getPresentableName() + "'");
          }
          dependencies.add(moduleIds[dependencyIndex]);
        }
        int resourcePathsCount = in.readInt();
        List<String> resourcePaths = new ArrayList<>(resourcePathsCount);
        for (int j = 0; j < resourcePathsCount; j++) {
          resourcePaths.add(in.readUTF());
        }

        descriptors.put(descriptorId, RawRuntimeModuleDescriptor.create(descriptorId, visibility, resourcePaths, dependencies));
      }

      int pluginHeadersCount = in.readInt();
      List<RawRuntimePluginHeader> pluginHeaders = new ArrayList<>(pluginHeadersCount);
      for (int i = 0; i < pluginHeadersCount; i++) {
        String pluginId = in.readUTF();
        int pluginDescriptorModuleIdIndex = in.readInt();
        if (pluginDescriptorModuleIdIndex < 0 || pluginDescriptorModuleIdIndex >= totalModuleIdCount) {
          throw new MalformedRepositoryException("Invalid plugin descriptor module index '" + pluginDescriptorModuleIdIndex + "' for '" + pluginId + "'");
        }
        RuntimeModuleId pluginDescriptorModuleId = moduleIds[pluginDescriptorModuleIdIndex];
        int includedModulesCount = in.readInt();
        List<RawIncludedRuntimeModule> includedModules = new ArrayList<>(includedModulesCount);
        for (int j = 0; j < includedModulesCount; j++) {
          int includedModuleIndex = in.readInt();
          if (includedModuleIndex < 0 || includedModuleIndex >= totalModuleIdCount) {
            throw new MalformedRepositoryException("Invalid included module index '" + includedModuleIndex + "' for '" + pluginId + "'");
          }
          RuntimeModuleId moduleId = moduleIds[includedModuleIndex];
          int loadingRuleIndex = in.read();
          if (loadingRuleIndex < 0 || loadingRuleIndex >= LOADING_RULES_BY_INDEX.length) {
            throw new MalformedRepositoryException("Invalid loading rule index '" + loadingRuleIndex + "' for '" + moduleId.getPresentableName() + "' in '" + pluginId + "'");
          }
          int requiredIfAvailableIndex = in.readInt();
          if (requiredIfAvailableIndex < -1 || requiredIfAvailableIndex > totalModuleIdCount) {
            throw new MalformedRepositoryException("Invalid required-if-available index '" + requiredIfAvailableIndex + "' for '" + moduleId.getPresentableName() + "' in '" + pluginId + "'");
          }
          RuntimeModuleId requiredIfAvailable = requiredIfAvailableIndex == -1 ? null : moduleIds[requiredIfAvailableIndex];
          includedModules.add(new RawIncludedRuntimeModule(moduleId, LOADING_RULES_BY_INDEX[loadingRuleIndex], requiredIfAvailable));
        }
        pluginHeaders.add(RawRuntimePluginHeader.create(pluginId, pluginDescriptorModuleId, includedModules));
      }

      return RawRuntimeModuleRepositoryData.create(descriptors, pluginHeaders, filePath.getParent());
    }
  }

  public static @NotNull String @Nullable [] loadBootstrapClasspath(@NotNull Path binaryFile, @NotNull String bootstrapModuleName) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(binaryFile)))) {
      int formatVersion = in.readInt();
      if (formatVersion != FORMAT_VERSION) return null;
      
      in.readInt();

      boolean hasBootstrap = in.readBoolean();
      if (!hasBootstrap) return null;
      
      String actualBootstrapModuleName = in.readUTF();
      if (!actualBootstrapModuleName.equals(bootstrapModuleName)) return null;
      
      int size = in.readInt();
      String[] classpath = new String[size];
      for (int i = 0; i < size; i++) {
        classpath[i] = in.readUTF();
      }
      return classpath;
    }
  }

  static void skipGeneratorVersionAndBootstrapClasspath(DataInputStream in) throws IOException {
    in.readInt();//generator version

    boolean hasBootstrapClasspath = in.readBoolean();
    if (hasBootstrapClasspath) {
      in.readUTF();
      int size = in.readInt();
      for (int i = 0; i < size; i++) {
        in.readUTF();
      }
    }
  }
}
