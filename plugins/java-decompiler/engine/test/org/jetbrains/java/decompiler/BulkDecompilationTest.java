// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.junit.Assert.assertTrue;

public class BulkDecompilationTest {
  private DecompilerTestFixture fixture;

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp();
  }

  @After
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testDirectory() {
    File classes = new File(fixture.getTempDir(), "classes");
    unpack(new File(fixture.getTestDataDir(), "bulk.jar"), classes);

    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSpace(classes, true);
    decompiler.decompileContext();

    assertFilesEqual(new File(fixture.getTestDataDir(), "bulk"), fixture.getTargetDir());
  }

  @Test
  public void testJar() {
    doTestJar("bulk");
  }

  @Test
  public void testObfuscated() {
    doTestJar("obfuscated");
  }

  private void doTestJar(String name) {
    ConsoleDecompiler decompiler = fixture.getDecompiler();
    String jarName = name + ".jar";
    decompiler.addSpace(new File(fixture.getTestDataDir(), jarName), true);
    decompiler.decompileContext();

    File unpacked = new File(fixture.getTempDir(), "unpacked");
    unpack(new File(fixture.getTargetDir(), jarName), unpacked);

    assertFilesEqual(new File(fixture.getTestDataDir(), name), unpacked);
  }

  private static void unpack(File archive, File targetDir) {
    try (ZipFile zip = new ZipFile(archive)) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          File file = new File(targetDir, entry.getName());
          assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
          try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(file)) {
            InterpreterUtil.copyStream(in, out);
          }
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}