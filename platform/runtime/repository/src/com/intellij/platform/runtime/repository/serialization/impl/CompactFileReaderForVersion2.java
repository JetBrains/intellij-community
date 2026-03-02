// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CompactFileReaderForVersion2 {
  static @NotNull RawRuntimeModuleRepositoryData loadFromInputStream(@NotNull Path filePath, DataInputStream in) throws IOException {
    CompactFileReader.skipGeneratorVersionAndBootstrapClasspath(in);

    boolean hasMainPluginModule = in.readBoolean();
    if (hasMainPluginModule) {
      in.readUTF();//skip main plugin module name
    }

    Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptors = new HashMap<>();

    int descriptorsCount = in.readInt();
    int unresolvedDependenciesCount = in.readInt();
    int totalIdCount = descriptorsCount + unresolvedDependenciesCount;
    RuntimeModuleId[] descriptorIds = new RuntimeModuleId[totalIdCount];
    for (int i = 0; i < totalIdCount; i++) {
      descriptorIds[i] = RuntimeModuleId.raw(in.readUTF());
    }

    for (int i = 0; i < descriptorsCount; i++) {
      RuntimeModuleId descriptorId = descriptorIds[i];
      int dependenciesCount = in.readInt();
      List<RuntimeModuleId> dependencies = new ArrayList<>(dependenciesCount);
      for (int j = 0; j < dependenciesCount; j++) {
        int dependencyIndex = in.readInt();
        if (dependencyIndex < 0 || dependencyIndex >= totalIdCount) {
          throw new MalformedRepositoryException("Invalid dependency index '" + dependencyIndex + "' in '" + descriptorId.getPresentableName() + "'");
        }
        dependencies.add(descriptorIds[dependencyIndex]);
      }
      int resourcePathsCount = in.readInt();
      List<String> resourcePaths = new ArrayList<>(resourcePathsCount);
      for (int j = 0; j < resourcePathsCount; j++) {
        resourcePaths.add(in.readUTF());
      }

      descriptors.put(descriptorId, RawRuntimeModuleDescriptor.create(descriptorId, resourcePaths, dependencies));
    }
    return RawRuntimeModuleRepositoryData.create(descriptors, Collections.emptyList(), filePath.getParent());
  }
}
