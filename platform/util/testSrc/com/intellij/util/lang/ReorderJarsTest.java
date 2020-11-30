// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.ReorderJarsMain;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class ReorderJarsTest {
  private static final Logger LOG = Logger.getInstance(ReorderJarsTest.class);
  private Path myTempDirectory;

  @Before
  public void setUp() throws Exception {
    myTempDirectory = Files.createTempDirectory("ReorderJarsTest.");
  }

  @After
  public void tearDown() {
    try {
      FileUtil.delete(myTempDirectory);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "plugins/reorderJars";
  }

  @Test
  public void testReordering() throws IOException {
    String path = getTestDataPath();

    try (ZipFile zipFile1 = new ZipFile(path + "/annotations.jar")) {
      List<ZipEntry> entries = ContainerUtil.toList(zipFile1.entries());
      LOG.debug(String.valueOf(entries));
    }

    ReorderJarsMain.main(new String[]{path + "/order.txt", path, myTempDirectory.toString()});

    File[] files = myTempDirectory.toFile().listFiles();
    assertThat(files).isNotNull();
    assertThat(files).hasSize(1);
    File file = files[0];
    assertThat(file.getName()).isEqualTo("annotations.jar");

    byte[] data;
    try (org.apache.commons.compress.archivers.zip.ZipFile zipFile2 = new org.apache.commons.compress.archivers.zip.ZipFile(file)) {
      List<ZipArchiveEntry> entries = ContainerUtil.toList(zipFile2.getEntriesInPhysicalOrder());

      LOG.debug(String.valueOf(entries));
      assertThat(entries.get(0).getName()).isEqualTo(JarMemoryLoader.SIZE_ENTRY);
      ZipArchiveEntry entry = entries.get(1);
      data = FileUtilRt.loadBytes(zipFile2.getInputStream(entry), (int)entry.getSize());
      assertThat(data).hasSize(548);
      assertThat(entry.getName()).isEqualTo("org/jetbrains/annotations/Nullable.class");
      assertThat(entries.get(2).getName()).isEqualTo("org/jetbrains/annotations/NotNull.class");
      assertThat(entries.get(3).getName()).isEqualTo("META-INF/MANIFEST.MF");
    }

    try (ZipFile zipFile3 = new ZipFile(file)) {
      JarMemoryLoader loader = JarMemoryLoader.load(zipFile3, file.toURI().toURL(), null);
      assertThat(loader).isNotNull();
      Resource resource = loader.getResource("org/jetbrains/annotations/Nullable.class");
      assertThat(resource).isNotNull();
      byte[] bytes = resource.getBytes();
      assertThat(bytes).hasSize(548);
      assertThat(Arrays.equals(data, bytes)).isTrue();
    }
  }

  @Test
  public void testPluginXml() throws Exception {
    String path = getTestDataPath();

    ReorderJarsMain.main(new String[]{path + "/zkmOrder.txt", path, myTempDirectory.toString()});

    File[] files = myTempDirectory.toFile().listFiles();
    assertThat(files).isNotNull();
    File file = files[0];
    assertThat(file.getName()).isEqualTo("zkm.jar");

    try (ZipFile zipFile = new ZipFile(file)) {
      List<ZipEntry> entries = ContainerUtil.toList(zipFile.entries());
      LOG.debug(entries.toString());
      assertThat(entries.get(0).getName()).isEqualTo(JarMemoryLoader.SIZE_ENTRY);
      assertThat(entries.get(1).getName()).isEqualTo("META-INF/plugin.xml");
    }
  }
}
