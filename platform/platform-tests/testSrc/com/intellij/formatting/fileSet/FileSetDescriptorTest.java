// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.formatting.FileSetTestCase;

public class FileSetDescriptorTest extends FileSetTestCase {

  public void testSimpleFileMask() {
    FileSetDescriptor entry = new FileSetDescriptor("*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertTrue(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testFileMaskWithDir() {
    FileSetDescriptor entry = new FileSetDescriptor("/src/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertFalse(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testExtendedDirSpec() {
    FileSetDescriptor entry = new FileSetDescriptor("/src/**/test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/dir2/test/file1.java")));
    assertFalse(entry.matches(getProject(), createFile("src/dir1/dir2/test/dir3/file1.java")));
    FileSetDescriptor entry2 = new FileSetDescriptor("/src/**/test/**/*.java");
    assertTrue(entry2.matches(getProject(), createFile("src/test/a/file1.java")));
    assertTrue(entry2.matches(getProject(), createFile("src/b/test/a/file1.java")));
  }

  public void testAnyCharMarks() {
    FileSetDescriptor entry = new FileSetDescriptor("/test-??/**/test?.java");
    assertFalse(entry.matches(getProject(), createFile("test-1/test2.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test3.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test/testa.java")));
  }


  public void testRelativeDirTest() {
    FileSetDescriptor entry = new FileSetDescriptor("test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("test/file2.java")));
    assertTrue(entry.matches(getProject(), createFile("a/b/c/d/test/file3.java")));
  }



}
