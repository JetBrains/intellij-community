/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.vfs;

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.intellij.mock.MockVirtualFile.dir;
import static com.intellij.mock.MockVirtualFile.file;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VfsUtilLightTest extends BareTestFixtureTestCase {
  private static VirtualFile myRoot;

  @BeforeClass
  public static void setUp() {
    myRoot =
      dir("/",
          dir("A",
              dir("B",
                  file("f")),
              dir("C",
                  file("f")),
              file("f")));
  }

  @AfterClass
  public static void tearDown() {
    myRoot = null;
  }

  @Test public void relativePathDirToDir() { assertRelativePath("A/B", "A/C", "../C"); }          // VfsUtil.getPath: "C"
  @Test public void relativePathDirToFile() { assertRelativePath("A/B", "A/C/f", "../C/f"); }     // VfsUtil.getPath: "C/f"
  @Test public void relativePathFileToDir() { assertRelativePath("A/B/f", "A/C", "../C"); }
  @Test public void relativePathFileToFile() { assertRelativePath("A/B/f", "A/C/f", "../C/f"); }
  @Test public void downwardPathDirToDir() { assertRelativePath("A", "A/B", "B"); }
  @Test public void downwardPathDirToFile() { assertRelativePath("A", "A/f", "f"); }
  @Test public void downwardPathFileToDir() { assertRelativePath("A/f", "A/B", "B"); }
  @Test public void downwardPathFileToFile() { assertRelativePath("A/f", "A/B/f", "B/f"); }
  @Test public void upwardPathFromDir() { assertRelativePath("A/B", "A", ".."); }                 // VfsUtil.getPath: ""
  @Test public void upwardPathFromFile() { assertRelativePath("A/B/f", "A", ".."); }              // VfsUtil.getPath: "../"
  @Test public void relativePathSameDir() { assertRelativePath("A/B", "A/B", ""); }
  @Test public void relativePathSameFile() { assertRelativePath("A/f", "A/f", "f"); }             // VfsUtil.getPath: ""

  private static void assertRelativePath(String srcPath, String dstPath, String expected) {
    VirtualFile src = myRoot.findFileByRelativePath(srcPath);
    assertNotNull(srcPath, src);
    VirtualFile dst = myRoot.findFileByRelativePath(dstPath);
    assertNotNull(dstPath, dst);
    assertEquals(expected, VfsUtilCore.findRelativePath(src, dst, '/'));
  }
}