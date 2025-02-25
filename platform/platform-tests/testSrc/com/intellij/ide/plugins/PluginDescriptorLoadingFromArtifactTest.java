// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.util.io.Compressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginDescriptorLoadingFromArtifactTest {
  @Test void descriptorLoadingFromJarArtifact(@TempDir Path tempDir) throws IOException {
    var jarFile = tempDir.resolve("test.plugin.jar");
    try (var jar = new Compressor.Jar(jarFile)) {
      jar.addFile(PluginManagerCore.META_INF + "/ext-plugin.xml", "<idea-plugin/>".getBytes(StandardCharsets.UTF_8));
      jar.addFile(PluginManagerCore.PLUGIN_XML_PATH, "<idea-plugin><id>test.plugin</id></idea-plugin>".getBytes(StandardCharsets.UTF_8));
    }

    var descriptor = PluginDescriptorLoader.readBasicDescriptorDataFromArtifact(jarFile);
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getPluginId().getIdString()).isEqualTo("test.plugin");

    descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(jarFile, null);
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getPluginId().getIdString()).isEqualTo("test.plugin");
  }

  @Test void descriptorLoadingFromZipArtifact(@TempDir Path tempDir) throws IOException {
    var clientFile = tempDir.resolve("client.jar");
    try (var jar = new Compressor.Jar(clientFile)) {
      jar.addFile(PluginManagerCore.PLUGIN_XML_PATH, "<idea-plugin><id>test.plugin.client</id></idea-plugin>".getBytes(StandardCharsets.UTF_8));
    }
    var otherFile = tempDir.resolve("other.jar");
    try (var jar = new Compressor.Jar(otherFile)) {
      jar.addFile("file.txt", "...".getBytes(StandardCharsets.UTF_8));
    }
    var mainFile = tempDir.resolve("main.jar");
    try (var jar = new Compressor.Jar(mainFile)) {
      jar.addFile(PluginManagerCore.META_INF + "/ext-plugin.xml", "<idea-plugin/>".getBytes(StandardCharsets.UTF_8));
      jar.addFile(PluginManagerCore.PLUGIN_XML_PATH, "<idea-plugin><id>test.plugin</id></idea-plugin>".getBytes(StandardCharsets.UTF_8));
    }
    var zipFile = tempDir.resolve("test.plugin.zip");
    try (var zip = new Compressor.Zip(zipFile)) {
      zip.addFile("test.plugin/lib/client/plugin.jar", clientFile);
      zip.addFile("test.plugin/lib/other.jar", otherFile);
      zip.addFile("test.plugin/lib/plugin.jar", mainFile);
    }

    var descriptor = PluginDescriptorLoader.readBasicDescriptorDataFromArtifact(zipFile);
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getPluginId().getIdString()).isEqualTo("test.plugin");

    descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(zipFile, null);
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getPluginId().getIdString()).isEqualTo("test.plugin");
  }
}
