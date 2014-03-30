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
package com.intellij.diagnostic;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class VMOptionsTest {
  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = FileUtil.createTempFile("vmoptions.", ".txt");
    writeFile("-Xmx512m\n" +
              "-XX:MaxPermSize=128m");
    VMOptions.setTestFile(myFile.getPath());
  }

  @After
  public void tearDown() {
    VMOptions.clearTestFile();
    FileUtil.delete(myFile);
  }

  @Test
  public void testRegExpr() {
    Pattern p = Pattern.compile("-Xmx(\\d*)([a-zA-Z]*)");

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
  public void testMacOsRegExpr() {
    Pattern p = Pattern.compile("<key>" + VMOptions.MAC_ARCH_VM_OPTIONS + "</key>\\s*<string>(.*)</string>");

    Matcher m = p.matcher("<plist version=\"1.0\">\n" +
                          "  <dict>\n" +
                          "    <dict>\n" +
                          "      <key>" + VMOptions.MAC_ARCH_VM_OPTIONS + "</key>\n" +
                          "      <string>-option -Xmx128mb -option</string>\n" +
                          "   </dict>\n" +
                          "  </dict>\n" +
                          "</plist>");
    assertTrue(m.find());

    assertEquals("-option -Xmx128mb -option", m.group(1));
  }

  @Test
  public void testReading() {
    assertEquals(512, VMOptions.readXmx());
    assertEquals(128, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingEmpty() {
    writeFile("");

    assertEquals(-1, VMOptions.readXmx());
    assertEquals(-1, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingKilos() {
    writeFile("-Xmx512000k\n" +
              "-XX:MaxPermSize=128000K  -XX:ReservedCodeCacheSize=256000K");

    assertEquals(512000 / 1024, VMOptions.readXmx());
    assertEquals(128000 / 1024, VMOptions.readMaxPermGen());
    assertEquals(256000 / 1024, VMOptions.readCodeCache());
  }

  @Test
  public void testReadingGigs() {
    writeFile("-Xmx512g\n" +
              "-XX:MaxPermSize=128G");

    assertEquals(512 * 1024, VMOptions.readXmx());
    assertEquals(128 * 1024, VMOptions.readMaxPermGen());
  }

  @Test
  public void testReadingWithoutUnit() {
    writeFile("-Xmx512\n" +
              "-XX:MaxPermSize=128");

    assertEquals(512, VMOptions.readXmx());
    assertEquals(128, VMOptions.readMaxPermGen());
  }

  @Test
  public void testWriting() {
    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertEquals("-Xmx1024m\n" +
                 "-XX:MaxPermSize=256m",
                 readFile());
  }

  @Test
  public void testWritingPreservingLocation() {
    writeFile("-someOption\n" +
              "-Xmx512m\n" +
              "-XX:MaxPermSize=128m\n" +
              "-anotherOption");

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertEquals("-someOption\n" +
                 "-Xmx1024m\n" +
                 "-XX:MaxPermSize=256m\n" +
                 "-anotherOption",
                 readFile());
  }

  @Test
  public void testWritingNew() {
    writeFile("-someOption");

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);
    VMOptions.writeCodeCache(256);

    assertEquals("-someOption -Xmx1024m -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m",
                 readFile());
  }

  @Test
  public void testWritingReadOnlyFile() {
    FileUtil.setReadOnlyAttribute(myFile.getPath(), true);

    VMOptions.writeXmx(1024);
    VMOptions.writeMaxPermGen(256);

    assertEquals("-Xmx1024m\n" +
                 "-XX:MaxPermSize=256m",
                 readFile());
  }


  private String readFile() {
    try {
      return FileUtil.loadFile(myFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeFile(String content) {
    @SuppressWarnings("SSBasedInspection") byte[] bytes = content.getBytes();
    try {
      FileUtil.writeToFile(myFile, bytes);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
