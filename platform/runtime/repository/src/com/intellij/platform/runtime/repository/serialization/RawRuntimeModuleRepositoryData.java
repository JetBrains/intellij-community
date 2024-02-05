// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
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

  RawRuntimeModuleRepositoryData(@NotNull Path descriptorsJarPath) throws XMLStreamException, IOException {
    myBasePath = descriptorsJarPath.getParent();
    myDescriptors = JarFileSerializer.loadFromJar(descriptorsJarPath);
  }

  @VisibleForTesting
  public RawRuntimeModuleRepositoryData(Path basePath, Map<String, RawRuntimeModuleDescriptor> descriptors) {
    myBasePath = basePath;
    myDescriptors = descriptors;
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
}
