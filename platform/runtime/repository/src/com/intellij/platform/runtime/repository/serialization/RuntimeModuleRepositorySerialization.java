// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl;
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public final class RuntimeModuleRepositorySerialization {
  private RuntimeModuleRepositorySerialization() {}

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @Nullable String bootstrapModuleName,
                               @NotNull Path jarPath, int generatorVersion) throws IOException {
    saveToJar(descriptors, bootstrapModuleName, jarPath, null, generatorVersion);
  }

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @Nullable String bootstrapModuleName,
                               @NotNull Path jarPath, @Nullable String mainPluginModuleId, int generatorVersion)
    throws IOException {
    try {
      JarFileSerializer.saveToJar(descriptors, bootstrapModuleName, jarPath, mainPluginModuleId, generatorVersion);
    }
    catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  public static @NotNull RawRuntimeModuleRepositoryData loadFromJar(@NotNull Path jarPath) throws MalformedRepositoryException {
    try {
      return JarFileSerializer.loadFromJar(jarPath);
    }
    catch (XMLStreamException | IOException e) {
      throw new MalformedRepositoryException("Failed to load repository from " + jarPath, e);
    }
  }

  public static @NotNull RuntimeModuleRepository loadFromRawData(@NotNull Path descriptorsJarPath,
                                                                 @NotNull RawRuntimeModuleRepositoryData rawRuntimeModuleRepositoryData) {
    return new RuntimeModuleRepositoryImpl(descriptorsJarPath, rawRuntimeModuleRepositoryData);
  }
}
