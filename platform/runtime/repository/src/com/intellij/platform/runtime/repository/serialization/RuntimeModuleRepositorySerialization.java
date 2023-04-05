// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public final class RuntimeModuleRepositorySerialization {
  private RuntimeModuleRepositorySerialization() {}

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @NotNull Path jarPath, int generatorVersion)
    throws IOException {
    try {
      JarFileSerializer.saveToJar(descriptors, jarPath, generatorVersion);
    }
    catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  public static @NotNull Map<String, RawRuntimeModuleDescriptor> loadFromJar(@NotNull Path jarPath) {
    try {
      return JarFileSerializer.loadFromJar(jarPath);
    }
    catch (XMLStreamException | IOException e) {
      throw new MalformedRepositoryException("Failed to load repository from " + jarPath, e);
    }
  }
}
