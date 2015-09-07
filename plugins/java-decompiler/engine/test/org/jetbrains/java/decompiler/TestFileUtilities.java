/*
 * Copyright 2015 JetBrains s.r.o.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.java.decompiler.util.InterpreterUtil;

final class TestFileUtilities {

  private TestFileUtilities() {
  }

  static void compareFolderContents(final File folder1, final File folder2) throws IOException {
    final List<File> folder1Files = collectFiles(folder1);
    final List<File> folder2Files = collectFiles(folder2);
    assertEquals("Number of files don't match!", folder1Files.size(), folder2Files.size());
    for (int i = 0, to = folder1Files.size(); i < to; i++) {
      assertSameContentIgnoreWhitespace(folder1Files.get(i), folder2Files.get(i));
    }
  }

  private static List<File> collectFiles(final File folder) {
    if (!folder.exists() || !folder.isDirectory()) {
      return Collections.emptyList();
    }
    final List<File> files = new ArrayList<File>();
    collectFiles(folder, files);
    Collections.sort(files);
    return files;
  }

  private static void collectFiles(final File folder, final Collection<File> files) {
    for (final File file : folder.listFiles()) {
      if (file.isDirectory()) {
        collectFiles(file, files);
      } else if (file.isFile()) {
        files.add(file);
      }
    }
  }

  private static List<String> readLines(final File file) throws IOException {
    final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    final List<String> statements = new ArrayList<String>();
    try {
      while (true) {
        final String readLine = in.readLine();
        if (null == readLine) {
          return statements;
        }
        final String trimmedLine = readLine.trim();
        if (!trimmedLine.isEmpty()) {
          statements.add(trimmedLine);
        }
      }
    } finally {
      in.close();
    }
  }

  private static void assertSameContentIgnoreWhitespace(final File expected, final File actual) throws IOException {
    final List<String> expectedLines = readLines(expected);
    final List<String> actualLines = readLines(actual);
    assertEquals("File lines don't match!", expectedLines.size(), actualLines.size());
    for (int i = 0, to = expectedLines.size(); i < to; i++) {
      assertEquals("Lines differ", expectedLines.get(i), actualLines.get(i));
    }
  }

  static void unpack(final File archive, final File targetDir) throws IOException {
    final ZipFile zip = new ZipFile(archive);
    try {
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          final File file = new File(targetDir, entry.getName());
          assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
          final InputStream in = zip.getInputStream(entry);
          final OutputStream out = new FileOutputStream(file);
          InterpreterUtil.copyStream(in, out);
          out.close();
          in.close();
        }
      }
    } finally {
      zip.close();
    }
  }

}
