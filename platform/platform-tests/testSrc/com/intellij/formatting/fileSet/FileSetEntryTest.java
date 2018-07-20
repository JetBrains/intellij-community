// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class FileSetEntryTest extends PlatformTestCase {

  public void testSimpleFileMask() {
    FileSetEntry entry = new FileSetEntry("*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertTrue(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testFileMaskWithDir() {
    FileSetEntry entry = new FileSetEntry("/src/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test.java")));
    assertFalse(entry.matches(getProject(), createFile("src/test.txt")));
    assertFalse(entry.matches(getProject(), createFile("file3.java")));
  }

  public void testExtendedDirSpec() {
    FileSetEntry entry = new FileSetEntry("/src/**/test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("src/dir1/dir2/test/file1.java")));
    assertFalse(entry.matches(getProject(), createFile("src/dir1/dir2/test/dir3/file1.java")));
    FileSetEntry entry2 = new FileSetEntry("/src/**/test/**/*.java");
    assertTrue(entry2.matches(getProject(), createFile("src/test/a/file1.java")));
    assertTrue(entry2.matches(getProject(), createFile("src/b/test/a/file1.java")));
  }

  public void testAnyCharMarks() {
    FileSetEntry entry = new FileSetEntry("/test-??/**/test?.java");
    assertFalse(entry.matches(getProject(), createFile("test-1/test2.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test3.java")));
    assertTrue(entry.matches(getProject(), createFile("test-12/test/testa.java")));
  }


  public void testRelativeDirTest() {
    FileSetEntry entry = new FileSetEntry("test/*.java");
    assertTrue(entry.matches(getProject(), createFile("src/test/file1.java")));
    assertTrue(entry.matches(getProject(), createFile("test/file2.java")));
    assertTrue(entry.matches(getProject(), createFile("a/b/c/d/test/file3.java")));
  }

  private VirtualFile createFile(@NotNull String path) {
    String[] dirNames = path.split("/");
    VirtualFile baseDir = getProject().getBaseDir();
    for (int i = 0; i < dirNames.length - 1; i ++) {
      VirtualFile existing = VfsUtilCore.findRelativeFile(dirNames[i], baseDir);
      if (existing == null) {
        baseDir = createChildDirectory(baseDir, dirNames[i]);
      }
      else {
        baseDir = existing;
      }
    }
    return createChildData(baseDir, dirNames[dirNames.length - 1]);
  }

}
