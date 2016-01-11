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
    ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSpace(new File(fixture.getTestDataDir(), "bulk.jar"), true);
    decompiler.decompileContext();

    File unpacked = new File(fixture.getTempDir(), "unpacked");
    unpack(new File(fixture.getTargetDir(), "bulk.jar"), unpacked);

    assertFilesEqual(new File(fixture.getTestDataDir(), "bulk"), unpacked);
  }

  private static void unpack(File archive, File targetDir) {
    try {
      ZipFile zip = new ZipFile(archive);
      try {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.isDirectory()) {
            File file = new File(targetDir, entry.getName());
            assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
            InputStream in = zip.getInputStream(entry);
            OutputStream out = new FileOutputStream(file);
            InterpreterUtil.copyStream(in, out);
            out.close();
            in.close();
          }
        }
      }
      finally {
        zip.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}