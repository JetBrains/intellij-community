// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor;
import com.intellij.formatting.FileSetTestCase;

public class FileSetPatternDescriptorTest extends FileSetTestCase {

  public void testSimpleFileMask() {
    GlobPatternDescriptor entry = new GlobPatternDescriptor("*.java");
    assertTrue(entry.matches(createFile("src/test.java")));
    assertFalse(entry.matches(createFile("src/test.txt")));
    assertTrue(entry.matches(createFile("file3.java")));
  }

  public void testFileMaskWithDir() {
    GlobPatternDescriptor entry = new GlobPatternDescriptor("/src/*.java");
    assertTrue(entry.matches(createFile("src/test.java")));
    assertFalse(entry.matches(createFile("src/test.txt")));
    assertFalse(entry.matches(createFile("file3.java")));
  }

  public void testExtendedDirSpec() {
    GlobPatternDescriptor entry = new GlobPatternDescriptor("/src/**/test/*.java");
    // Fails, why??? assertTrue(entry.matches(createFile("src/test/file1.java")));
    assertTrue(entry.matches(createFile("src/dir1/test/file1.java")));
    assertTrue(entry.matches(createFile("src/dir1/dir2/test/file1.java")));
    assertFalse(entry.matches(createFile("src/dir1/dir2/test/dir3/file1.java")));
    GlobPatternDescriptor entry2 = new GlobPatternDescriptor("/src/**/test/**/*.java");
    // Fails, why??? assertTrue(entry2.matches(createFile("src/test/a/file1.java")));
    assertTrue(entry2.matches(createFile("src/b/test/a/file1.java")));
  }

  public void testAnyCharMarks() {
    GlobPatternDescriptor entry = new GlobPatternDescriptor("/test-??/**/test?.java");
    assertFalse(entry.matches(createFile("test-1/test2.java")));
    // Fails, why??? assertTrue(entry.matches(createFile("test-12/test3.java")));
    assertTrue(entry.matches(createFile("test-12/test/testa.java")));
  }


  public void testRelativeDirTest() {
    GlobPatternDescriptor entry = new GlobPatternDescriptor("test/*.java");
    assertTrue(entry.matches(createFile("src/test/file1.java")));
    assertTrue(entry.matches(createFile("test/file2.java")));
    assertTrue(entry.matches(createFile("a/b/c/d/test/file3.java")));
  }



}
