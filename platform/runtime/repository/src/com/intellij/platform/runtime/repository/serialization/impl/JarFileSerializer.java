// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimePluginHeader;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization.JarEntryConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class JarFileSerializer {
  public static final String SPECIFICATION_VERSION = "0.1";
  public static final String SPECIFICATION_TITLE = "IntelliJ Runtime Module Repository";
  private static final Attributes.Name BOOTSTRAP_MODULE_ATTRIBUTE_NAME = new Attributes.Name("Bootstrap-Module-Name");
  private static final Attributes.Name BOOTSTRAP_CLASSPATH_ATTRIBUTE_NAME = new Attributes.Name("Bootstrap-Class-Path");
  /**
   * The time of every entry, fixed so that the same descriptors always give the same bytes. A ZIP entry stores a local
   * date-time, so it is set as one and no time zone can move it.
   */
  private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

  public static @NotNull RawRuntimeModuleRepositoryData loadFromJar(@NotNull Path jarPath) throws IOException, XMLStreamException {
    Map<RuntimeModuleId, RawRuntimeModuleDescriptor> rawData = new HashMap<>();
    try (JarInputStream input = new JarInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
      Manifest manifest = input.getManifest();
      if (manifest == null) {
        throw new IOException("Manifest not found in " + jarPath);
      }
      Attributes mainAttributes = manifest.getMainAttributes();
      String version = mainAttributes.getValue(Attributes.Name.SPECIFICATION_VERSION);
      if (version == null) {
        throw new IOException("'" + Attributes.Name.SPECIFICATION_VERSION.toString() + "' attribute is not specified in " + jarPath);
      }
      if (!version.equals(SPECIFICATION_VERSION)) {
        throw new IOException("'" + jarPath + "' has unsupported version '" + version + "' ('" + SPECIFICATION_VERSION + "' is expected)");
      }
      JarEntry entry;
      XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
      while ((entry = input.getNextJarEntry()) != null) {
        String name = entry.getName();
        if (name.endsWith(".xml") && name.indexOf('/') == -1) {
          RawRuntimeModuleDescriptor data = ModuleXmlSerializer.parseModuleXml(factory, input);
          rawData.put(data.getModuleId(), data);
        }
      }
    }
    return RawRuntimeModuleRepositoryData.create(rawData, Collections.emptyList(), jarPath.getParent());
  }

  public static @NotNull String @Nullable [] loadBootstrapClasspath(@NotNull Path jarPath, @NotNull String bootstrapModuleName)
    throws IOException {
    try (JarInputStream input = new JarInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
      Manifest manifest = input.getManifest();
      if (manifest == null) return null;
      Attributes attributes = manifest.getMainAttributes();
      if (!SPECIFICATION_VERSION.equals(attributes.getValue(Attributes.Name.SPECIFICATION_VERSION))) {
        return null;
      }
      if (!bootstrapModuleName.equals(attributes.getValue(BOOTSTRAP_MODULE_ATTRIBUTE_NAME))) {
        return null;
      }
      String classpathValue = attributes.getValue(BOOTSTRAP_CLASSPATH_ATTRIBUTE_NAME);
      if (classpathValue == null) {
        return null;
      }
      return classpathValue.split(" ");
    }
  }

  public static void saveToJar(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors,
                               @NotNull Collection<RuntimePluginHeader> pluginHeaders,
                               @Nullable String bootstrapModuleName,
                               @NotNull Path jarFile,
                               int generatorVersion)
    throws IOException, XMLStreamException {
    Files.createDirectories(jarFile.getParent());
    try (JarOutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile)))) {
      writeEntries(descriptors, pluginHeaders, bootstrapModuleName, generatorVersion, (name, content) -> {
        jarOutput.putNextEntry(newEntry(name));
        jarOutput.write(content);
        jarOutput.closeEntry();
      });
    }
  }

  /**
   * Gives the entries of the JAR file to {@code consumer}. The manifest comes first, because {@link JarInputStream} finds it only there.
   */
  public static void writeEntries(@NotNull Collection<RawRuntimeModuleDescriptor> descriptors,
                                  @NotNull Collection<RuntimePluginHeader> pluginHeaders,
                                  @Nullable String bootstrapModuleName,
                                  int generatorVersion,
                                  @NotNull JarEntryConsumer consumer)
    throws IOException, XMLStreamException {
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.put(Attributes.Name.SPECIFICATION_TITLE, SPECIFICATION_TITLE);
    attributes.put(Attributes.Name.SPECIFICATION_VERSION, SPECIFICATION_VERSION);
    attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, SPECIFICATION_VERSION + "." + generatorVersion);
    if (bootstrapModuleName != null) {
      attributes.put(BOOTSTRAP_MODULE_ATTRIBUTE_NAME, bootstrapModuleName);
      Collection<String> bootstrapClasspath = CachedClasspathComputation.computeBootstrapClasspath(descriptors, bootstrapModuleName);
      attributes.put(BOOTSTRAP_CLASSPATH_ATTRIBUTE_NAME, String.join(" ", bootstrapClasspath));
    }
    var buffer = new ByteArrayOutputStream();
    manifest.write(buffer);
    consumer.accept(JarFile.MANIFEST_NAME, buffer.toByteArray());
    XMLOutputFactory factory = XMLOutputFactory.newDefaultFactory();
    for (RawRuntimeModuleDescriptor descriptor : descriptors) {
      String moduleName = descriptor.getModuleId().getName();
      String namespace = descriptor.getModuleId().getNamespace();
      String fileName = namespace.equals(RuntimeModuleId.DEFAULT_NAMESPACE) ? moduleName : moduleName + "_" + namespace;
      buffer.reset();
      ModuleXmlSerializer.writeModuleXml(descriptor, new PrintWriter(buffer, false, StandardCharsets.UTF_8), factory);
      consumer.accept(fileName + ".xml", buffer.toByteArray());
    }
    for (RuntimePluginHeader pluginHeader : pluginHeaders) {
      String moduleName = pluginHeader.getPluginDescriptorModuleId().getName();
      buffer.reset();
      PluginHeaderXmlSerializer.writePluginHeaderXml(pluginHeader, new PrintWriter(buffer, false, StandardCharsets.UTF_8), factory);
      consumer.accept("plugins/" + moduleName + ".xml", buffer.toByteArray());
    }
  }

  private static @NotNull JarEntry newEntry(@NotNull String name) {
    JarEntry entry = new JarEntry(name);
    entry.setTimeLocal(ENTRY_TIME);
    return entry;
  }
}
