// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

public final class JarFileSerializer {
  public static final String SPECIFICATION_VERSION = "0.1";
  public static final String SPECIFICATION_TITLE = "IntelliJ Runtime Module Repository";
  private static final Attributes.Name BOOTSTRAP_MODULE_ATTRIBUTE_NAME = new Attributes.Name("Bootstrap-Module-Name");
  private static final Attributes.Name BOOTSTRAP_CLASSPATH_ATTRIBUTE_NAME = new Attributes.Name("Bootstrap-Class-Path");

  @NotNull
  public static Map<String, RawRuntimeModuleDescriptor> loadFromJar(@NotNull Path jarPath) throws IOException, XMLStreamException {
    Map<String, RawRuntimeModuleDescriptor> rawData = new HashMap<>();
    try (JarInputStream input = new JarInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
      Manifest manifest = input.getManifest();
      if (manifest == null) {
        throw new IOException("Manifest not found in " + jarPath);
      }
      String version = manifest.getMainAttributes().getValue(Attributes.Name.SPECIFICATION_VERSION);
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
        if (name.endsWith(".xml")) {
          RawRuntimeModuleDescriptor data = ModuleXmlSerializer.parseModuleXml(factory, input);
          rawData.put(data.getId(), data);
        }
      }
    }
    return rawData;
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
                               @Nullable String bootstrapModuleName,
                               @NotNull Path jarFile, int generatorVersion)
    throws IOException, XMLStreamException {
    Files.createDirectories(jarFile.getParent());
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.put(Attributes.Name.SPECIFICATION_TITLE, SPECIFICATION_TITLE);
    attributes.put(Attributes.Name.SPECIFICATION_VERSION, SPECIFICATION_VERSION);
    attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, SPECIFICATION_VERSION + "." + generatorVersion);
    if (bootstrapModuleName != null) {
      attributes.put(BOOTSTRAP_MODULE_ATTRIBUTE_NAME, bootstrapModuleName);
      attributes.put(BOOTSTRAP_CLASSPATH_ATTRIBUTE_NAME, computeClasspath(descriptors, bootstrapModuleName));
    }
    try (JarOutputStream jarOutput = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile)), manifest)) {
      XMLOutputFactory factory = XMLOutputFactory.newDefaultFactory();
      for (RawRuntimeModuleDescriptor descriptor : descriptors) {
        String id = descriptor.getId();
        jarOutput.putNextEntry(new JarEntry(id + ".xml"));
        PrintWriter output = new PrintWriter(jarOutput, false, StandardCharsets.UTF_8);
        ModuleXmlSerializer.writeModuleXml(descriptor, output, factory);
        jarOutput.closeEntry();
      }
    }
  }

  private static String computeClasspath(Collection<RawRuntimeModuleDescriptor> descriptors, String moduleName) {
    Set<String> classpath = new LinkedHashSet<>();
    Map<String, RawRuntimeModuleDescriptor> descriptorMap = new HashMap<>();
    for (RawRuntimeModuleDescriptor descriptor : descriptors) {
      descriptorMap.put(descriptor.getId(), descriptor);
    }
    collectClasspathEntries(moduleName, descriptorMap, new HashSet<String>(), classpath);
    return String.join(" ", classpath);
  }

  private static void collectClasspathEntries(String moduleName,
                                              Map<String, RawRuntimeModuleDescriptor> descriptorMap,
                                              Set<String> processedModules,
                                              Set<String> classpath) {
    if (!processedModules.add(moduleName)) return;
    RawRuntimeModuleDescriptor descriptor = descriptorMap.get(moduleName);
    if (descriptor == null) return;
    classpath.addAll(descriptor.getResourcePaths());
    for (String dependency : descriptor.getDependencies()) {
      collectClasspathEntries(dependency, descriptorMap, processedModules, classpath);
    }
  }
}
