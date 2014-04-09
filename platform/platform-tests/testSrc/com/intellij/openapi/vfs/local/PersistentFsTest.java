/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.PlatformLangTestCase;

import java.io.File;

public class PersistentFsTest extends PlatformLangTestCase {
  private PersistentFS myFs;
  private LocalFileSystem myLocalFs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFs = PersistentFS.getInstance();
    myLocalFs = LocalFileSystem.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myLocalFs = null;
    myFs = null;
    super.tearDown();
  }

  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());

    VirtualFile vFile = myLocalFs.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();
    assertSame(vFile, myFs.findFileById(id));

    vFile.delete(this);
    assertNull(myFs.findFileById(id));
  }

  public void testFindRootShouldNotBeFooledByRelativePath() throws Exception {
    File tmp = createTempDirectory();
    File x = new File(tmp, "x.jar");
    assertTrue(x.createNewFile());

    VirtualFile vx = myLocalFs.refreshAndFindFileByIoFile(x);
    assertNotNull(vx);

    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);
    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    assertSame(myFs.findRoot(path, jfs), root);
  }

  public void testDeleteSubstRoots() throws Exception {
    if (!SystemInfo.isWindows) return;

    File tempDirectory = FileUtil.createTempDirectory(getTestName(false), null);
    File substRoot = IoTestUtil.createSubst(tempDirectory.getPath());
    VirtualFile subst = myLocalFs.refreshAndFindFileByIoFile(substRoot);
    assertNotNull(subst);

    try {
      final File[] children = substRoot.listFiles();
      assertNotNull(children);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);

    VirtualFile[] roots = myFs.getRoots(myLocalFs);
    for (VirtualFile root : roots) {
      String rootPath = root.getPath();
      String prefix = StringUtil.commonPrefix(rootPath, substRoot.getPath());
      assertEmpty(prefix);
    }
  }

  public void testLocalRoots() {
    VirtualFile tempRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tempRoot);

    VirtualFile[] roots = myFs.getLocalRoots();
    for (VirtualFile root : roots) {
      assertTrue("root=" + root, root.isInLocalFileSystem());
      VirtualFileSystem fs = root.getFileSystem();
      assertTrue("fs=" + fs, fs instanceof LocalFileSystem);
      assertFalse("fs=" + fs, fs instanceof TempFileSystem);
    }
  }

  public void testInvalidJarRootsIgnored() throws Exception {
    File file = IoTestUtil.createTestFile("file.txt");
    String url = "jar://" + FileUtil.toSystemIndependentName(file.getPath()) + "!/";
    assertNull(VirtualFileManager.getInstance().findFileByUrl(url));
  }
}
