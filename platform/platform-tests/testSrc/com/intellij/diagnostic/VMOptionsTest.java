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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import kotlin.collections.ArraysKt;
import org.jetbrains.annotations.NotNull;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VMOptionsTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = myTempDir.newFile("vmoptions.txt");
    FileUtil.writeToFile(myFile, "-Xmx512m\n-XX:MaxMetaspaceSize=128m");
    System.setProperty("jb.vmOptionsFile", myFile.getPath());
  }

  @After
  public void tearDown() {
    System.clearProperty("jb.vmOptionsFile");
  }

  @Test
  public void testReading() {
    assertEquals(512, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void testReadingEmpty() throws IOException {
    FileUtil.writeToFile(myFile, "");

    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void testReadingKilos() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512000k -XX:MaxMetaspaceSize=128000K -XX:ReservedCodeCacheSize=256000K");

    assertEquals(512000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
    assertEquals(256000 / 1024, VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, false));
  }

  @Test
  public void testReadingGigs() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512g\n-XX:MaxMetaspaceSize=128G");

    assertEquals(512 * 1024, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128 * 1024, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void testReadingWithoutUnit() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512\n-XX:MaxMetaspaceSize=128");

    assertEquals(512, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void testWriting() throws IOException {
    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.METASPACE, 512);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxMetaspaceSize=512m");
  }

  @Test
  public void testWritingPreservingLocation() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n-Xmx512m\n-XX:MaxMetaspaceSize=128m\n-anotherOption");

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.METASPACE, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption -Xmx1024m -XX:MaxMetaspaceSize=256m -anotherOption");
  }

  @Test
  public void testWritingNew() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption");

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.METASPACE, 256);
    VMOptions.writeOption(VMOptions.MemoryKind.CODE_CACHE, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption -Xmx1024m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=256m");
  }

  @Test
  public void testWritingReadOnlyFile() throws IOException {
    FileUtil.setReadOnlyAttribute(myFile.getPath(), true);

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.METASPACE, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxMetaspaceSize=256m");
  }

  @Test
  public void testWritingNonExistingFile() throws IOException {
    FileUtil.delete(myFile);

    VMOptions.writeOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.writeOption(VMOptions.MemoryKind.METASPACE, 256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m -XX:MaxMetaspaceSize=256m");
  }

  @Test
  public void testWritingCDSArchiveFileFromScratch() throws IOException {
    FileUtil.delete(myFile);

    String myCDSFile = "a/b/c/cds-for-test.jsa";
    VMOptions.writeEnableCDSArchiveOption(myCDSFile);

    String text = FileUtil.loadFile(myFile);

    assertEquals("-Xshare:auto\n" +
                 "-XX:+UnlockDiagnosticVMOptions\n" +
                 "-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa",
                 StringUtil.convertLineSeparators(text));
  }

  @Test
  public void testWritingCDSArchiveFileFromXdumpClash() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n" +
                                 "-Xmx512m\n" +
                                 "-XX:MaxMetaspaceSize=128m\n" +
                                 "-anotherOption\n" +
                                 "-Xshare:dump\n" +
                                 "junk");

    String myCDSFile = "a/b/c/cds-for-test.jsa";
    VMOptions.writeEnableCDSArchiveOption(myCDSFile);

    String text = FileUtil.loadFile(myFile);
    assertEquals("-someOption\n" +
                 "-Xmx512m\n" +
                 "-XX:MaxMetaspaceSize=128m\n" +
                 "-anotherOption\n" +
                 "-Xshare:auto\n" +
                 "junk\n" +
                 "-XX:+UnlockDiagnosticVMOptions\n" +
                 "-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa",
                 StringUtil.convertLineSeparators(text));
  }

  @Test
  public void testWritingCDSArchiveFileFromXXClash() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n" +
                                 "-Xmx512m\n" +
                                 "-XX:MaxMetaspaceSize=128m\n" +
                                 "-anotherOption\n" +
                                 "-XX:+UnlockDiagnosticVMOptions\n" +
                                 "junk");

    String myCDSFile = "a/b/c/cds-for-test.jsa";
    VMOptions.writeEnableCDSArchiveOption(myCDSFile);

    String text = FileUtil.loadFile(myFile);
    System.out.println(text);

    assertEquals("-someOption\n" +
                 "-Xmx512m\n" +
                 "-XX:MaxMetaspaceSize=128m\n" +
                 "-anotherOption\n" +
                 "-XX:+UnlockDiagnosticVMOptions\n" +
                 "junk\n" +
                 "-Xshare:auto\n" +
                 "-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa",
                 StringUtil.convertLineSeparators(text));
  }

  @Test
  public void testWritingCDSArchiveFileFromArchiveClash() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n-Xmx512m\n-XX:MaxMetaspaceSize=128m\n-anotherOption\n-XX:SharedArchiveFile=foo-bar\njunk");

    String myCDSFile = "cds-for-test.jsa";
    VMOptions.writeEnableCDSArchiveOption(myCDSFile);

    String text = FileUtil.loadFile(myFile);
    System.out.println(text);
    assertEquals("-someOption\n" +
                 "-Xmx512m\n" +
                 "-XX:MaxMetaspaceSize=128m\n" +
                 "-anotherOption\n" +
                 "-XX:SharedArchiveFile=cds-for-test.jsa\n" +
                 "junk\n" +
                 "-Xshare:auto\n" +
                 "-XX:+UnlockDiagnosticVMOptions",
                 StringUtil.convertLineSeparators(text));
  }


  @Test
  public void testWritingCDSDisableScratch() throws IOException {
    FileUtil.writeToFile(myFile, "");

    VMOptions.writeDisableCDSArchiveOption();

    String text = FileUtil.loadFile(myFile);
    assertEquals("",
                 StringUtil.convertLineSeparators(text));
  }

  @Test
  public void testWritingCDSDisableFull() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n" +
                                 "-Xmx512m\n" +
                                 "-XX:MaxMetaspaceSize=128m\n" +
                                 "-anotherOption\n" +
                                 "-XX:+UnlockDiagnosticVMOptions\n" +
                                 "junk\n" +
                                 "-Xshare:auto\n" +
                                 "junk2\n" +
                                 "-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa\n" +
                                 "junk3");

    VMOptions.writeDisableCDSArchiveOption();

    //let's make sure we do not remove anything extra
    assertEquals("-someOption\n" +
                 "-Xmx512m\n" +
                 "-XX:MaxMetaspaceSize=128m\n" +
                 "-anotherOption\n" +
                 "-XX:+UnlockDiagnosticVMOptions\n" +
                 "junk\n" +
                 "junk2\n" +
                 "junk3", StringUtil.convertLineSeparators(FileUtil.loadFile(myFile)));
  }

  @Test
  public void testWritingCDSDisableXShare() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n" +
                                 "-Xmx512m\n" +
                                 "-XX:MaxMetaspaceSize=128m\n" +
                                 "-anotherOption\n" +
                                 "-XX:+UnlockDiagnosticVMOptions\n" +
                                 "-Xshare:dump\n" +
                                 "-XX:SharedArchiveFile=545\njunk");

    VMOptions.writeDisableCDSArchiveOption();

    //let's make sure we do not remove anything extra
    assertEquals("-someOption\n" +
                 "-Xmx512m\n" +
                 "-XX:MaxMetaspaceSize=128m\n" +
                 "-anotherOption\n" +
                 "-XX:+UnlockDiagnosticVMOptions\n" +
                 "junk",
                 StringUtil.convertLineSeparators(FileUtil.loadFile(myFile)));
  }
}