/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import junit.framework.TestCase;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class UpdateableZipTest extends TestCase {
  private File zipFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    zipFile = createTestUtilZip();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(zipFile);
    } finally {
      super.tearDown();
    }
  }

  public void testRead() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");
    }
  }

  public void testAppendEntry() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      JBZipEntry newEntry = jbZip.getOrCreateEntry("/third");
      newEntry.setData("third".getBytes());
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry thirdEntry = utilZip.getEntry("/third");
      assertNotNull(thirdEntry);
      String thirdText = FileUtil.loadTextAndClose(new InputStreamReader(utilZip.getInputStream(thirdEntry)));
      assertEquals("third", thirdText);
    }
  }

  public void testReplaceEntryContent() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      JBZipEntry newEntry = jbZip.getOrCreateEntry("/second");
      newEntry.setData("Content Replaced".getBytes());
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry updatedEntry = utilZip.getEntry("/second");
      assertNotNull(updatedEntry);
      String thirdText = FileUtil.loadTextAndClose(new InputStreamReader(utilZip.getInputStream(updatedEntry)));
      assertEquals("Content Replaced", thirdText);
    }
  }

  public void testRemoveEntry() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      jbZip.getEntry("/second").erase();
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry presentEntry = utilZip.getEntry("/first");
      assertNotNull(presentEntry);
      ZipEntry removedEntry = utilZip.getEntry("/second");
      assertNull(removedEntry);
    }
  }

  public void testGc() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      jbZip.getEntry("/second").erase();
      jbZip.gc();
    }

    try (JBZipFile jbZip = new JBZipFile(zipFile)) {
      assertEntryWithContentExists(jbZip, "/first", "first");
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry presentEntry = utilZip.getEntry("/first");
      assertNotNull(presentEntry);
      ZipEntry removedEntry = utilZip.getEntry("/second");
      assertNull(removedEntry);
    }

    try (RandomAccessFile file = new RandomAccessFile(zipFile, "r")) {
      int length = (int)file.length();
      byte[] buffer = new byte[length];
      file.readFully(buffer, 0, length);
      assertFalse(new String(buffer, CharsetToolkit.US_ASCII_CHARSET).contains("second"));
    }
  }

  public void testReadWrite1() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      createOrReplaceEntryData(jbZip, "/third", "third");

      assertEntryWithContentExists(jbZip, "/third", "third");
    }
  }

  public void testReadWrite2() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      createOrReplaceEntryData(jbZip, "/first", "first_new");

      assertEntryWithContentExists(jbZip, "/first", "first_new");
    }
  }

  public void testMissingSeeks() throws Exception {
    try (JBZipFile jbZip = new JBZipFile(zipFile)) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      //seek end
      createOrReplaceEntryData(jbZip, "/third", "third");
      //seek somewhere
      assertEntryWithContentExists(jbZip, "/first", "first");
      //write somewhere :)
      createOrReplaceEntryData(jbZip, "/forth", "forth");

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");
      assertEntryWithContentExists(jbZip, "/third", "third");
      assertEntryWithContentExists(jbZip, "/forth", "forth");
    }
  }

  private void createOrReplaceEntryData(JBZipFile jbZip, String name, String data) throws IOException {
    JBZipEntry newEntry = jbZip.getOrCreateEntry(name);
    newEntry.setData(data.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  /*
  public void testAppendToIdeaJar() throws Exception {
    //ProfilingUtil.startCPUProfiling();
    for (int i = 0; i < 20; i++) {
      long tm = System.currentTimeMillis();
      JBZipFile jbZip = new JBZipFile(new File("/Users/max/idea.jar"));
      long tm2 = System.currentTimeMillis();

      System.out.print("Loaded in: " + (tm2 - tm) + " msec ");

      jbZip.getOrCreateEntry("/somenewtext.txt").setData("New text".getBytes());
      jbZip.close();

      long tm3 = System.currentTimeMillis();
      System.out.println("Updated in: " + (tm3 - tm2) + " msec");
    }
    //ProfilingUtil.captureCPUSnapshot();
  }
  */


  private File createTestUtilZip() throws Exception {
    File zipFile = FileUtil.createTempFile("test", ".zip");
    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {

      appendEntry(zos, "/first", "first".getBytes());
      appendEntry(zos, "/second", "second".getBytes());
    }
    return zipFile;
  }

  private static void assertEntryWithContentExists(JBZipFile jbZip, String entryName, String content) throws IOException {
    JBZipEntry entry = jbZip.getEntry(entryName);
    assertNotNull(entry);
    String text = new String(entry.getData());
    assertEquals(content, text);
  }

  private void appendEntry(ZipOutputStream zos, String name, byte[] content) throws Exception{
    ZipEntry e = new ZipEntry(name);
    e.setMethod(ZipEntry.STORED);
    e.setSize(content.length);
    CRC32 crc = new CRC32();
    crc.update(content);
    e.setCrc(crc.getValue());
    zos.putNextEntry(e);
    zos.write(content, 0, content.length);
    zos.closeEntry();
  }
}
