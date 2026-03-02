// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl;
import com.intellij.platform.runtime.repository.serialization.impl.CompactFileReader;
import com.intellij.platform.runtime.repository.serialization.impl.CompactFileWriter;
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Runtime module repository is stored in two formats: a JAR file containing XML files describing modules, and a compact binary file.
 * Currently, files in both formats are generated. The compact binary file is used inside the IDE process to speed up loading, whereas
 * the JAR file is used by other tools like IntelliJ Platform Gradle Plugin, so it's kept for compatibility reasons.
 */
public final class RuntimeModuleRepositorySerialization {
  private RuntimeModuleRepositorySerialization() {}

  public static void saveToCompactFile(@NotNull Collection<RawRuntimeModuleDescriptor> moduleDescriptors,
                                       @NotNull Collection<RawRuntimePluginHeader> pluginHeaders,
                                       @Nullable String bootstrapModuleName,
                                       @NotNull Path filePath, int generatorVersion) throws IOException {
    CompactFileWriter.saveToFile(moduleDescriptors, pluginHeaders, bootstrapModuleName, generatorVersion, filePath);
  }

  /**
   * @deprecated {@code mainPluginModuleId} is not used anymore, use {@link #saveToCompactFile(Collection, String, Path, int)} instead
   */
  @Deprecated(forRemoval = true)
  public static void saveToCompactFile(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @Nullable String bootstrapModuleName,
                                       @NotNull Path filePath, @Nullable String mainPluginModuleId, int generatorVersion) throws IOException {
    saveToCompactFile(descriptors, Collections.emptyList(), bootstrapModuleName, filePath, generatorVersion);
  }

  /**
   * @deprecated use {@link #saveToJar(Collection, Collection, String, Path, int)} instead
   */
  @Deprecated(forRemoval = true)
  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors,
                               @Nullable String bootstrapModuleName,
                               @NotNull Path jarPath, int generatorVersion) throws IOException {
    saveToJar(descriptors, Collections.emptyList(), bootstrapModuleName, jarPath, generatorVersion);
  }

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors,
                               @NotNull Collection<RawRuntimePluginHeader> pluginHeaders,
                               @Nullable String bootstrapModuleName,
                               @NotNull Path jarPath, int generatorVersion) throws IOException {
    try {
      JarFileSerializer.saveToJar(descriptors, pluginHeaders, bootstrapModuleName, jarPath, generatorVersion);
    }
    catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * @deprecated {@code mainPluginModuleId} is not used anymore, use {@link #saveToJar(Collection, String, Path, int)} instead
   */
  @Deprecated(forRemoval = true)
  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors, @Nullable String bootstrapModuleName,
                               @NotNull Path jarPath, @Nullable String mainPluginModuleId, int generatorVersion)
    throws IOException {
    saveToJar(descriptors, Collections.emptyList(), bootstrapModuleName, jarPath, generatorVersion);
  }
  
  public static @NotNull RawRuntimeModuleRepositoryData loadFromCompactFile(@NotNull Path filePath) throws MalformedRepositoryException {
    try {
      return CompactFileReader.loadFromFile(filePath);
    }
    catch (IOException e) {
      throw new MalformedRepositoryException("Failed to load repository from " + filePath, e);
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

  @ApiStatus.Internal
  public static @Nullable Path getFallbackJarPath(@NotNull Path descriptorsFilePath) {
    if (descriptorsFilePath.getFileName().toString().endsWith(".jar")) {
      return descriptorsFilePath;
    }
    Path jarPath = descriptorsFilePath.getParent().resolve("module-descriptors.jar");
    if (!Files.exists(descriptorsFilePath) && Files.exists(jarPath)) {
      return jarPath;
    }
    return null;
  }

  /**
   * Loads lists of relative paths composing the classpath for the bootstrap module {@code bootstrapModuleName}.
   * If the bootstrap classpath wasn't stored explicitly, {@code null} is returned.
   */
  public static @NotNull String @Nullable [] loadBootstrapClasspath(@NotNull Path descriptorsFilePath, @NotNull String bootstrapModuleName)
    throws IOException {
    Path fallbackJarPath = getFallbackJarPath(descriptorsFilePath);
    if (fallbackJarPath != null) {
      return JarFileSerializer.loadBootstrapClasspath(fallbackJarPath, bootstrapModuleName);
    }
    return CompactFileReader.loadBootstrapClasspath(descriptorsFilePath, bootstrapModuleName);
  }
}
