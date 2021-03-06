// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.application.options.codeStyle.excludedFiles.PatternDescriptor;
import com.intellij.formatting.FileSetTestCase;

public class FileSetPatternDescriptorTest extends FileSetTestCase {

  public void testSimpleFileMask() {
    PatternDescriptor entry = new PatternDescriptor("*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertTrue(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testFileMaskWithDir() {
    PatternDescriptor entry = new PatternDescriptor("/src/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertFalse(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testExtendedDirSpec() {
    PatternDescriptor entry = new PatternDescriptor("/src/**/test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/dir2/test/file1.java")));
    assertFalse(entry.matches(getProject(), createFile("src/dir1/dir2/test/dir3/file1.java")));
    PatternDescriptor entry2 = new PatternDescriptor("/src/**/test/**/*.java");
    assertTrue(entry2.matches(getProject(), createFile("src/test/a/file1.java")));
    assertTrue(entry2.matches(getProject(), createFile("src/b/test/a/file1.java")));
  }

  public void testAnyCharMarks() {
    PatternDescriptor entry = new PatternDescriptor("/test-??/**/test?.java");
    assertFalse(entry.matches(getProject(), createFile("test-1/test2.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test3.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test/testa.java")));
  }


  public void testRelativeDirTest() {
    PatternDescriptor entry = new PatternDescriptor("test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("test/file2.java")));
    assertTrue(entry.matches(getProject(), createFile("a/b/c/d/test/file3.java")));
  }



}
