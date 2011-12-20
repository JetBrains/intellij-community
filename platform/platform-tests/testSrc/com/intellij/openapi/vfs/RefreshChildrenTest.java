/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformLangTestCase;

import java.io.*;

/**
 * @author yole
 */
public class RefreshChildrenTest extends PlatformLangTestCase {
  private File testDir;

  @Override
  protected void setUp() throws Exception {
    // the superclass sets tmp. dir on every run and cleans up, but we want to do it our way
    String baseTempDir = FileUtil.getTempDirectory();
    testDir = new File(baseTempDir, "RefreshChildrenTest." + getName());

    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(testDir);
    super.tearDown();
  }

  public void testRefreshSeesLatestDirectoryContents() throws Exception {
    assertFalse(testDir.exists());
    assert testDir.exists() || testDir.mkdir() : testDir;
    assertTrue(testDir.isDirectory());
    writeFile(testDir, "Foo.java", "");

    LocalFileSystem local = LocalFileSystem.getInstance();
    VirtualFile virtualDir = local.findFileByIoFile(testDir);
    assert virtualDir != null : virtualDir;
    virtualDir.getChildren();
    virtualDir.refresh(false, true);

    checkChildCount(virtualDir, 1);

    writeFile(testDir, "Bar.java", "");
    virtualDir.refresh(false, true);

    checkChildCount(virtualDir, 2);
  }

  private static void writeFile(File dir, String filename, String contents) throws IOException {
    Writer writer = new OutputStreamWriter(new FileOutputStream(new File(dir, filename)), "UTF-8");
    try {
      writer.write(contents);
    }
    finally {
      writer.close();
    }
  }

  private static void checkChildCount(VirtualFile virtualDir, int expectedCount) {
    VirtualFile[] children = virtualDir.getChildren();
    if (children.length != expectedCount) {
      System.err.println("children:");
      for (VirtualFile child : children) {
        System.err.println(child.getPath());
      }
    }
    assertEquals(expectedCount, children.length);
  }
}
