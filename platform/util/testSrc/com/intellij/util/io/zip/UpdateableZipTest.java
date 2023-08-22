// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import junit.framework.TestCase;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class UpdateableZipTest extends TestCase {
  protected File zipFile;

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

  public static class UpdateableZip64Test extends UpdateableZipTest {
    @NotNull
    @Override
    protected JBZipFile createZip() throws IOException {
      return new JBZipFile(zipFile, StandardCharsets.UTF_8, false, ThreeState.YES);
    }

    @Override
    protected void modifyArchive(ZipArchiveOutputStream zos) {
      zos.setUseZip64(Zip64Mode.Always);
    }

    public void testBigZip() throws Exception {
      File zipFile = FileUtil.createTempFile("big-test", ".zip");
      String expectedEntryText = "first";

      // add entries up to 6 GB (more than 4 GB - 1 byte)
      int i = 0;
      try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
        modifyArchive(zos);
        while (Files.size(zipFile.toPath()) <= 6 * 1024 * 1024) {
          appendEntry(zos, "/entry" + i++, expectedEntryText.getBytes(StandardCharsets.UTF_8));
        }
      }

      List<JBZipEntry> entries;
      try (JBZipFile zip = new JBZipFile(zipFile, StandardCharsets.UTF_8, true, ThreeState.YES)) {
        entries = zip.getEntries();
        for (JBZipEntry entry : entries) {
          String text = new String(entry.getData(), StandardCharsets.UTF_8);
          assertEquals(expectedEntryText, text);
        }
        assertEquals(i, entries.size());
      }

      // add another entries
      try (JBZipFile zip = new JBZipFile(zipFile, StandardCharsets.UTF_8, false, ThreeState.YES)) {
        for (int j = 0; j < 50000; j++) {
          zip.getOrCreateEntry("/entry" + i++)
            .setDataFromStream(new ByteArrayInputStream(expectedEntryText.getBytes(StandardCharsets.UTF_8)));
        }
      }

      List<JBZipEntry> entries2;
      try (JBZipFile zip = new JBZipFile(zipFile, StandardCharsets.UTF_8, true, ThreeState.YES)) {
        entries2 = zip.getEntries();
        for (JBZipEntry entry : entries2) {
          String text = new String(entry.getData(), StandardCharsets.UTF_8);
          assertEquals(expectedEntryText, text);
        }
        assertEquals(i, entries2.size());
      }

      // add another entries using other method
      try (JBZipFile zip = new JBZipFile(zipFile, StandardCharsets.UTF_8, false, ThreeState.YES)) {
        for (int j = 0; j < 50000; j++) {
          zip.getOrCreateEntry("/entry" + i++).setData(expectedEntryText.getBytes(StandardCharsets.UTF_8));
        }
      }

      List<JBZipEntry> entries3;
      try (JBZipFile zip = new JBZipFile(zipFile, StandardCharsets.UTF_8, true, ThreeState.YES)) {
        entries3 = zip.getEntries();
        for (JBZipEntry entry : entries3) {
          String text = new String(entry.getData(), StandardCharsets.UTF_8);
          assertEquals(expectedEntryText, text);
        }
        assertEquals(i, entries3.size());
      }
    }
  }

  @NotNull
  protected JBZipFile createZip() throws IOException {
    return new JBZipFile(zipFile);
  }

  public void testRead() throws Exception {
    try (JBZipFile jbZip = createZip()) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");
    }
  }

  public void testAppendEntry() throws Exception {
    try (JBZipFile jbZip = createZip()) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      JBZipEntry newEntry = jbZip.getOrCreateEntry("/third");
      newEntry.setData("third".getBytes(StandardCharsets.UTF_8));
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry thirdEntry = utilZip.getEntry("/third");
      assertNotNull(thirdEntry);
      String thirdText = FileUtil.loadTextAndClose(new InputStreamReader(utilZip.getInputStream(thirdEntry), StandardCharsets.UTF_8));
      assertEquals("third", thirdText);
    }
  }

  public void testReplaceEntryContent() throws Exception {
    try (JBZipFile jbZip = createZip()) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      JBZipEntry newEntry = jbZip.getOrCreateEntry("/second");
      newEntry.setData("Content Replaced".getBytes(StandardCharsets.UTF_8));
    }

    try (ZipFile utilZip = new ZipFile(zipFile)) {
      ZipEntry updatedEntry = utilZip.getEntry("/second");
      assertNotNull(updatedEntry);
      String thirdText = FileUtil.loadTextAndClose(new InputStreamReader(utilZip.getInputStream(updatedEntry), StandardCharsets.UTF_8));
      assertEquals("Content Replaced", thirdText);
    }
  }

  public void testRemoveEntry() throws Exception {
    try (JBZipFile jbZip = createZip()) {

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
    try (JBZipFile jbZip = createZip()) {

      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      jbZip.getEntry("/second").erase();
      jbZip.gc();
    }

    try (JBZipFile jbZip = createZip()) {
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
      assertFalse(new String(buffer, StandardCharsets.US_ASCII).contains("second"));
    }
  }

  public void testReadWrite1() throws Exception {
    try (JBZipFile jbZip = createZip()) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      createOrReplaceEntryData(jbZip, "/third", "third");

      assertEntryWithContentExists(jbZip, "/third", "third");
    }
  }

  public void testReadWrite2() throws Exception {
    try (JBZipFile jbZip = createZip()) {
      assertEntryWithContentExists(jbZip, "/first", "first");
      assertEntryWithContentExists(jbZip, "/second", "second");

      createOrReplaceEntryData(jbZip, "/first", "first_new");

      assertEntryWithContentExists(jbZip, "/first", "first_new");
    }
  }

  public void testMissingSeeks() throws Exception {
    try (JBZipFile jbZip = createZip()) {
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
    newEntry.setData(data.getBytes(StandardCharsets.UTF_8));
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
    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
      modifyArchive(zos);
      appendEntry(zos, "/first", "first".getBytes(StandardCharsets.UTF_8));
      appendEntry(zos, "/second", "second".getBytes(StandardCharsets.UTF_8));
    }
    return zipFile;
  }

  protected void modifyArchive(ZipArchiveOutputStream zos) {

  }

  private static void assertEntryWithContentExists(JBZipFile jbZip, String entryName, String content) throws IOException {
    JBZipEntry entry = jbZip.getEntry(entryName);
    assertNotNull(entry);
    String text = new String(entry.getData(), StandardCharsets.UTF_8);
    assertEquals(content, text);
  }

  protected void appendEntry(ZipArchiveOutputStream zos, String name, byte[] content) throws Exception{
    ZipArchiveEntry e = new ZipArchiveEntry(name);
    e.setMethod(ZipEntry.STORED);
    e.setSize(content.length);
    CRC32 crc = new CRC32();
    crc.update(content);
    e.setCrc(crc.getValue());
    zos.putArchiveEntry(e);
    zos.write(content, 0, content.length);
    zos.closeArchiveEntry();
  }
}
