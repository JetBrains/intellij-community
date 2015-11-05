/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class VMOptionsTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = myTempDir.newFile("vmoptions.txt");
    FileUtil.writeToFile(myFile, "-Xmx512m\n-XX:MaxPermSize=128m");
    VMOptions.setTestFile(myFile.getPath());
  }

  @After
  public void tearDown() {
    VMOptions.clearTestFile();
  }

  @Test
  public void testRegExpr() {
    Pattern p = VMOptions.MemoryKind.HEAP.pattern;

    Matcher m = p.matcher("-option -Xmx128mb -option");
    assertTrue(m.find());
    assertEquals("128", m.group(1));
    assertEquals("mb", m.group(2));

    m = p.matcher("-option -Xmx -option");
    assertTrue(m.find());
    assertEquals("", m.group(1));
    assertEquals("", m.group(2));

    m = p.matcher("-option -Xxx -option");
    assertFalse(m.find());
  }

  @Test
  public void testReading() {
    assertEquals(512, VMOptions.readXmx());
    assertEquals(128, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingEmpty() throws IOException {
    FileUtil.writeToFile(myFile, "");

    assertEquals(-1, VMOptions.readXmx());
    assertEquals(-1, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingKilos() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512000k -XX:MaxPermSize=128000K -XX:ReservedCodeCacheSize=256000K");

    assertEquals(512000 / 1024, VMOptions.readXmx());
    assertEquals(128000 / 1024, VMOptions.readMaxPermGen());
    assertEquals(256000 / 1024, VMOptions.readCodeCache());
  }

  @Test
  public void testReadingGigs() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512g\n-XX:MaxPermSize=128G");

    assertEquals(512 * 1024, VMOptions.readXmx());
    assertEquals(128 * 1024, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingWithoutUnit() throws IOException {
    FileUtil.writeToFile(myFile, "-Xmx512\n-XX:MaxPermSize=128");

    assertEquals(512, VMOptions.readXmx());
    assertEquals(128, VMOptions.readMaxPermGen());
  }

  @Test
  public void testWriting() throws IOException {
    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m\n-XX:MaxPermSize=256m");
  }

  @Test
  public void testWritingPreservingLocation() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption\n-Xmx512m\n-XX:MaxPermSize=128m\n-anotherOption");

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption\n-Xmx1024m\n-XX:MaxPermSize=256m\n-anotherOption");
  }

  @Test
  public void testWritingNew() throws IOException {
    FileUtil.writeToFile(myFile, "-someOption");

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);
    VMOptions.writeCodeCache(256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-someOption\n-Xmx1024m\n-XX:MaxPermSize=256m\n-XX:ReservedCodeCacheSize=256m");
  }

  @Test
  public void testWritingReadOnlyFile() throws IOException {
    FileUtil.setReadOnlyAttribute(myFile.getPath(), true);

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertThat(FileUtil.loadFile(myFile)).isEqualToIgnoringWhitespace("-Xmx1024m\n-XX:MaxPermSize=256m");
  }

  @Test
  public void testWritingNonExistingFile() throws IOException {
    File testFile = myTempDir.newFile("vmoptions.non.existing.txt");
    FileUtil.delete(testFile);
    VMOptions.setTestFile(testFile.getPath());

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertThat(FileUtil.loadFile(testFile)).isEqualToIgnoringWhitespace("-Xmx1024m\n-XX:MaxPermSize=256m");
  }
}