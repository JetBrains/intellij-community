/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class VMOptionsTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = myTempDir.newFile("vmoptions.txt");
    FileUtil.writeToFile(myFile, "-Xmx512m\n-XX:MaxPermSize=128m");
    System.setProperty("jb.vmOptionsFile", myFile.getPath());
  }

  @After
  public void tearDown() {
    System.clearProperty("jb.vmOptionsFile");
  }

  @Test
  public void testReading() {
    assertEquals(512, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.PERM_GEN, false));
  }

  @Test
  public void testReadingEmpty() throws IOException {
    FileUtil.writeToFile(myFile, "");

    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.PERM_GEN, false));
  }

  @Test
  public void testReadingKilos() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512000k -XX:MaxPermSize=128000K -XX:ReservedCodeCacheSize=256000K");

    assertEquals(512000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.PERM_GEN, false));
    assertEquals(256000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, false));
  }

  @Test
  public void testReadingGigs() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512g\n-XX:MaxPermSize=128G");

    assertEquals(512 * 1024, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128 * 1024, VMOptions.readOption(VMOptions.MemoryKind.PERM_GEN, false));
  }

  @Test
  public void testReadingWithoutUnit() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512\n-XX:MaxPermSize=128");

    assertEquals(512, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.PERM_GEN, false));
  }

  @Test
  public void testWriting() throws IOException {
    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.PERM_GEN, 512);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxPermSize=512m");
  }

  @Test
  public void testWritingPreservingLocation() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n-Xmx512m\n-XX:MaxPermSize=128m\n-anotherOption");

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.PERM_GEN, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption -Xmx1024m -XX:MaxPermSize=256m -anotherOption");
  }

  @Test
  public void testWritingNew() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption");

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.PERM_GEN, 256);
    VMOptions.writeOption(VMOptions.MemoryKind.CODE_CACHE, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption -Xmx1024m -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m");
  }

  @Test
  public void testWritingReadOnlyFile() throws IOException {
    FileUtil.setReadOnlyAttribute(myFile.getPath(), true);

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.PERM_GEN, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxPermSize=256m");
  }

  @Test
  public void testWritingNonExistingFile() throws IOException {
    FileUtil.delete(myFile);

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.PERM_GEN, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxPermSize=256m");
  }
}