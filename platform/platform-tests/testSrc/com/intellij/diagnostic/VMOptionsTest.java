// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class VMOptionsTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private Path myFile;

  @Before
  public void setUp() throws IOException {
    myFile = tempDir.newFile("vmoptions.txt").toPath();
    Files.write(myFile, List.of("-Xmx512m", "-XX:MaxMetaspaceSize=128m"), VMOptions.getFileCharset());
    System.setProperty("jb.vmOptionsFile", myFile.toString());
  }

  @After
  public void tearDown() {
    System.clearProperty("jb.vmOptionsFile");
  }

  @Test
  public void reading() {
    assertEquals(512, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void readingEmpty() throws IOException {
    Files.write(myFile, ArrayUtil.EMPTY_BYTE_ARRAY);

    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(-1, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test
  public void readingUnits() throws IOException {
    Files.write(myFile, List.of("-Xmx2g", "-XX:MaxMetaspaceSize=128M", "-XX:ReservedCodeCacheSize=256000K"), VMOptions.getFileCharset());

    assertEquals(2 << 10, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(128, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
    assertEquals(256000 >> 10, VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, false));
  }

  @Test
  public void readingWithoutUnit() throws IOException {
    Files.write(myFile, List.of("-Xmx4194304", "-XX:MaxMetaspaceSize=128"), VMOptions.getFileCharset());

    assertEquals(4, VMOptions.readOption(VMOptions.MemoryKind.HEAP, false));
    assertEquals(0, VMOptions.readOption(VMOptions.MemoryKind.METASPACE, false));
  }

  @Test(expected = IllegalArgumentException.class)
  public void parsingInvalidMemoryOption() {
    VMOptions.parseMemoryOption("1b");
  }

  @Test
  public void writing() throws IOException {
    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.setOption(VMOptions.MemoryKind.METASPACE, 512);

    assertThat(myFile).hasContent("-Xmx1024m\n-XX:MaxMetaspaceSize=512m");
  }

  @Test
  public void writingAtNonExistentDirectory() throws IOException {
    Path anotherFile = tempDir.getRootPath().resolve("non-existent/file");
    System.setProperty("jb.vmOptionsFile", anotherFile.toString());

    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);

    assertThat(anotherFile).hasContent("-Xmx1024m");
  }

  @Test
  public void writingPreservingLocation() throws IOException {
    Files.write(myFile, List.of("-someOption", "-Xmx512m", "-XX:MaxMetaspaceSize=128m", "-anotherOption"), VMOptions.getFileCharset());

    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.setOption(VMOptions.MemoryKind.METASPACE, 256);

    assertThat(myFile).hasContent("-someOption\n-Xmx1024m\n-XX:MaxMetaspaceSize=256m\n-anotherOption");
  }

  @Test
  public void writingNew() throws IOException {
    Files.writeString(myFile, "-someOption", VMOptions.getFileCharset());

    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.setOption(VMOptions.MemoryKind.METASPACE, 256);
    VMOptions.setOption(VMOptions.MemoryKind.CODE_CACHE, 256);

    assertThat(myFile).hasContent("-someOption\n-Xmx1024m\n-XX:MaxMetaspaceSize=256m\n-XX:ReservedCodeCacheSize=256m");
  }

  @Test(expected = AccessDeniedException.class)
  public void writingReadOnlyFile() throws IOException {
    NioFiles.setReadOnly(myFile, true);
    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);
  }

  @Test
  public void writingNonExistingFile() throws IOException {
    Files.delete(myFile);

    VMOptions.setOption(VMOptions.MemoryKind.HEAP, 1024);
    VMOptions.setOption(VMOptions.MemoryKind.METASPACE, 256);

    assertThat(myFile).hasContent("-Xmx1024m\n-XX:MaxMetaspaceSize=256m");
  }

  @Test
  public void deletingAllEntries() throws IOException {
    Files.write(myFile, List.of("-Xmx128m", "-XX:MaxMetaspaceSize=240m", "-Xmx512m", "-Dx=y"), VMOptions.getFileCharset());

    VMOptions.setOption(VMOptions.MemoryKind.HEAP, -1);

    assertThat(myFile).hasContent("-XX:MaxMetaspaceSize=240m\n-Dx=y");
  }

  @Test
  public void charsetCompatibility() throws IOException {
    String name = SystemInfo.isWindows ? IoTestUtil.getUnicodeName(VMOptions.getFileCharset().name()) : IoTestUtil.getUnicodeName();
    assumeTrue("Cannot work on " + VMOptions.getFileCharset(), name != null);

    VMOptions.setProperty("my.test.property", name);
    assertThat(VMOptions.readOption("-Dmy.test.property=", false)).isEqualTo(name);
  }

  @Test
  public void writingCDSArchiveFileFromScratch() throws IOException {
    Files.delete(myFile);

    enableCDS("a/b/c/cds-for-test.jsa");

    assertThat(myFile).hasContent("-Xshare:auto\n-XX:+UnlockDiagnosticVMOptions\n-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa");
  }

  @Test
  public void writingCDSArchiveFileFromXDumpClash() throws IOException {
    Files.write(myFile, List.of("-someOption", "-Xmx512m", "-anotherOption", "-Xshare:dump", "junk"), VMOptions.getFileCharset());

    enableCDS("a/b/c/cds-for-test.jsa");

    assertThat(myFile).hasContent(
      "-someOption\n-Xmx512m\n-anotherOption\n-Xshare:auto\njunk\n-XX:+UnlockDiagnosticVMOptions\n-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa");
  }

  @Test
  public void writingCDSArchiveFileFromXXClash() throws IOException {
    Files.write(myFile, List.of("-someOption", "-Xmx512m", "-anotherOption", "-XX:+UnlockDiagnosticVMOptions", "junk"), VMOptions.getFileCharset());

    enableCDS("a/b/c/cds-for-test.jsa");

    assertThat(myFile).hasContent(
      "-someOption\n-Xmx512m\n-anotherOption\n-XX:+UnlockDiagnosticVMOptions\njunk\n-Xshare:auto\n-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa");
  }

  @Test
  public void writingCDSArchiveFileFromArchiveClash() throws IOException {
    Files.write(myFile, List.of("-someOption", "-Xmx512m", "-anotherOption", "-XX:SharedArchiveFile=foo-bar", "junk"), VMOptions.getFileCharset());

    enableCDS("cds-for-test.jsa");

    assertThat(myFile).hasContent(
      "-someOption\n-Xmx512m\n-anotherOption\n-XX:SharedArchiveFile=cds-for-test.jsa\njunk\n-Xshare:auto\n-XX:+UnlockDiagnosticVMOptions");
  }

  @Test
  public void writingCDSDisableScratch() throws IOException {
    Files.writeString(myFile, "", VMOptions.getFileCharset());

    disableCDS();

    assertThat(myFile).hasContent("");
  }

  @Test
  public void writingCDSDisableFull() throws IOException {
    List<String> lines = List.of(
      "-someOption", "-Xmx512m", "-anotherOption", "-XX:+UnlockDiagnosticVMOptions", "junk", "-Xshare:auto", "junk2",
      "-XX:SharedArchiveFile=a/b/c/cds-for-test.jsa", "junk3");
    Files.write(myFile, lines, VMOptions.getFileCharset());

    disableCDS();

    assertThat(myFile).hasContent("-someOption\n-Xmx512m\n-anotherOption\n-XX:+UnlockDiagnosticVMOptions\njunk\njunk2\njunk3");
  }

  @Test
  public void writingCDSDisableXShare() throws IOException {
    List<String> lines = List.of(
      "-someOption", "-Xmx512m", "-anotherOption", "-XX:+UnlockDiagnosticVMOptions", "-Xshare:dump", "-XX:SharedArchiveFile=545\njunk");
    Files.write(myFile, lines, VMOptions.getFileCharset());

    disableCDS();

    assertThat(myFile).hasContent("-someOption\n-Xmx512m\n-anotherOption\n-XX:+UnlockDiagnosticVMOptions\njunk");
  }

  private static void enableCDS(String path) throws IOException {
    VMOptions.setOptions(List.of(pair("-Xshare:", "auto"), pair("-XX:+UnlockDiagnosticVMOptions", ""), pair("-XX:SharedArchiveFile=", path)));
  }

  private static void disableCDS() throws IOException {
    VMOptions.setOptions(List.of(pair("-Xshare:", null), pair("-XX:SharedArchiveFile=", null)));
  }
}
