// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CompactFileReader {
  public static final int FORMAT_VERSION = 2;

  public static RawRuntimeModuleRepositoryData loadFromFile(@NotNull Path filePath) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(filePath)))) {
      int formatVersion = in.readInt();
      if (formatVersion != FORMAT_VERSION) {
        throw new MalformedRepositoryException("'" + filePath + "' has unsupported format '" + formatVersion + "'");
      }
      in.readInt();//generator version
      
      boolean hasBootstrapClasspath = in.readBoolean();
      if (hasBootstrapClasspath) {
        in.readUTF();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
          in.readUTF();
        }
      }
      
      boolean hasMainPluginModule = in.readBoolean();
      String mainPluginModuleName = hasMainPluginModule? in.readUTF() : null;
      
      Map<String, RawRuntimeModuleDescriptor> descriptors = new HashMap<>();
      
      int descriptorsCount = in.readInt();
      int unresolvedDependenciesCount = in.readInt();
      int totalIdCount = descriptorsCount + unresolvedDependenciesCount;
      String[] descriptorIds = new String[totalIdCount];
      for (int i = 0; i < totalIdCount; i++) {
        descriptorIds[i] = in.readUTF();
      }
      
      for (int i = 0; i < descriptorsCount; i++) {
        String descriptorId = descriptorIds[i];
        int dependenciesCount = in.readInt();
        List<String> dependencies = new ArrayList<>(dependenciesCount);
        for (int j = 0; j < dependenciesCount; j++) {
          int dependencyIndex = in.readInt();
          if (dependencyIndex < 0 || dependencyIndex >= totalIdCount) {
            throw new MalformedRepositoryException("Invalid dependency index '" + dependencyIndex + "' in '" + descriptorId + "'");
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
      return new RawRuntimeModuleRepositoryData(descriptors, filePath.getParent(), mainPluginModuleName);
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
}
