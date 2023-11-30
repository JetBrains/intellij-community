// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.Compressor;
import com.intellij.util.lang.ZipFilePool;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PluginDescriptorLoadingTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private boolean myResetPool = false;

  @Before
  public void setUp() throws Exception {
    if (ZipFilePool.POOL == null) {
      ZipFilePool.POOL = new ZipFilePoolImpl();
      myResetPool = true;
    }
  }

  @After
  public void tearDown() throws Exception {
    if (myResetPool) {
      ZipFilePool.POOL = null;
    }
  }

  @Test
  public void descriptorLoadingFromJarArtifact() throws IOException {
    File jarFile = tempDir.newFile("test.jar");
    try (Compressor.Jar jar = new Compressor.Jar(jarFile)) {
      jar.addFile(PluginManagerCore.PLUGIN_XML_PATH, "<idea-plugin><id>test.plugin</id></idea-plugin>".getBytes(StandardCharsets.UTF_8));
    }
    IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(jarFile.toPath(), null);
    assertNotNull(descriptor);
    assertEquals("test.plugin", descriptor.getPluginId().getIdString());
  }

  @Test
  public void descriptorLoadingFromZipArtifact() throws IOException {
    File jarFile = tempDir.newFile("test.jar");
    try (Compressor.Jar jar = new Compressor.Jar(jarFile)) {
      jar.addFile(PluginManagerCore.PLUGIN_XML_PATH, "<idea-plugin><id>test.plugin</id></idea-plugin>".getBytes(StandardCharsets.UTF_8));
    }

    File zipFile = tempDir.newFile("test.zip");
    try (Compressor.Zip zip = new Compressor.Zip(zipFile)) {
      zip.addFile("test.plugin/lib/plugin.jar", jarFile);
    }

    IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(zipFile.toPath(), null);
    assertNotNull(descriptor);
    assertEquals("test.plugin", descriptor.getPluginId().getIdString());
  }
}
