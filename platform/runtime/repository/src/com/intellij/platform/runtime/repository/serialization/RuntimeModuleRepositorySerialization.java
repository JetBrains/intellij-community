// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.ProductMode;
import com.intellij.platform.runtime.repository.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import com.intellij.platform.runtime.repository.serialization.impl.ProductModulesXmlLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public final class RuntimeModuleRepositorySerialization {
  private RuntimeModuleRepositorySerialization() {}

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @Nullable String bootstrapModuleName, 
                               @NotNull Path jarPath, int generatorVersion)
    throws IOException {
    try {
      JarFileSerializer.saveToJar(descriptors, bootstrapModuleName, jarPath, generatorVersion);
    }
    catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  public static @NotNull Map<String, RawRuntimeModuleDescriptor> loadFromJar(@NotNull Path jarPath) throws MalformedRepositoryException {
    try {
      return JarFileSerializer.loadFromJar(jarPath);
    }
    catch (XMLStreamException | IOException e) {
      throw new MalformedRepositoryException("Failed to load repository from " + jarPath, e);
    }
  }

  public static @NotNull ProductModules loadProductModules(@NotNull Path xmlFile, @NotNull ProductMode currentMode,
                                                           @NotNull RuntimeModuleRepository repository) {
    try {
      return loadProductModules(Files.newInputStream(xmlFile), xmlFile.toString(), currentMode, repository);
    }
    catch (IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + xmlFile, e);
    }
  }

  @NotNull
  public static ProductModules loadProductModules(@NotNull InputStream inputStream, @NotNull String filePath,
                                                  @NotNull ProductMode currentMode,
                                                  @NotNull RuntimeModuleRepository repository) {
    try {
      return ProductModulesXmlLoader.parseModuleXml(inputStream, filePath, currentMode, repository);
    }
    catch (XMLStreamException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + filePath, e);
    }
  }
}
