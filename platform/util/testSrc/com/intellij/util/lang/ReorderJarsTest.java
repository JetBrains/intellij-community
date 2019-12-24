/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import com.intellij.util.io.zip.ReorderJarsMain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
public class ReorderJarsTest {
  private static final Logger LOG = Logger.getInstance(ReorderJarsTest.class);
  private File myTempDirectory;

  @Before
  public void setUp() throws Exception {
    myTempDirectory = FileUtil.createTempDirectory("ReorderJarsTest.", ".tmp");
  }

  @After
  public void tearDown() {
    FileUtil.delete(myTempDirectory);
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "plugins/reorderJars";
  }

  @Test
  public void testReordering() throws IOException {
    String path = getTestDataPath();

    try (JBZipFile zipFile1 = new JBZipFile(path + "/annotations.jar")) {
      List<JBZipEntry> entries = zipFile1.getEntries();
      LOG.debug(String.valueOf(entries));
    }

    ReorderJarsMain.main(new String[]{path + "/order.txt", path, myTempDirectory.getPath()});

    File[] files = myTempDirectory.listFiles();
    assertNotNull(files);
    assertEquals(1, files.length);
    File file = files[0];
    assertEquals("annotations.jar", file.getName());

    byte[] data;
    try (JBZipFile zipFile2 = new JBZipFile(file)) {
      List<JBZipEntry> entries = zipFile2.getEntries();
      LOG.debug(String.valueOf(entries));
      assertEquals(JarMemoryLoader.SIZE_ENTRY, entries.get(0).getName());
      JBZipEntry entry = entries.get(1);
      data = entry.getData();
      assertEquals(548, data.length);
      assertEquals("org/jetbrains/annotations/Nullable.class", entry.getName());
      assertEquals("org/jetbrains/annotations/NotNull.class", entries.get(2).getName());
      assertEquals("META-INF/", entries.get(3).getName());
    }

    try (ZipFile zipFile3 = new ZipFile(file)) {
      JarMemoryLoader loader = JarMemoryLoader.load(zipFile3, file.toURI().toURL(), null);
      assertNotNull(loader);
      Resource resource = loader.getResource("org/jetbrains/annotations/Nullable.class");
      assertNotNull(resource);
      byte[] bytes = resource.getBytes();
      assertEquals(548, bytes.length);
      assertTrue(Arrays.equals(data, bytes));
    }
  }

  @Test
  public void testPluginXml() throws Exception {
    String path = getTestDataPath();

    ReorderJarsMain.main(new String[] { path + "/zkmOrder.txt", path, myTempDirectory.getPath() } );

    File[] files = myTempDirectory.listFiles();
    assertNotNull(files);
    File file = files[0];
    assertEquals("zkm.jar", file.getName());

    try (JBZipFile zipFile = new JBZipFile(file)) {
      List<JBZipEntry> entries = zipFile.getEntries();
      LOG.debug(String.valueOf(entries));
      assertEquals(JarMemoryLoader.SIZE_ENTRY, entries.get(0).getName());
      assertEquals("META-INF/plugin.xml", entries.get(1).getName());
    }
  }
}
