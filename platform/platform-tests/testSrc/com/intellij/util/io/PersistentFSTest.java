/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class PersistentFSTest extends PlatformTestCase {
  @Override
  public void setUp() throws Exception {
    initPlatformLangPrefix();
    super.setUp();
  }

  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();

    assertSame(vFile, PersistentFS.getInstance().findFileById(id));
    vFile.delete(this);

    assertNull(PersistentFS.getInstance().findFileById(id));
  }

  public void testListChildrenOfTheRootOfTheRoot() {
    PersistentFS fs = PersistentFS.getInstance();
    NewVirtualFile fakeRoot = fs.findRoot("", LocalFileSystem.getInstance());
    assertNotNull(fakeRoot);
    int users = fs.getId(fakeRoot, "Users", LocalFileSystem.getInstance());
    assertEquals(0, users);
    users = fs.getId(fakeRoot, "usr", LocalFileSystem.getInstance());
    assertEquals(0, users);
    int win = fs.getId(fakeRoot, "Windows", LocalFileSystem.getInstance());
    assertEquals(0, win);

    VirtualFile[] roots = fs.getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      int rid = fs.getId(fakeRoot, root.getName(), LocalFileSystem.getInstance());
      assertTrue(root.getPath()+"; Roots:"+ Arrays.toString(roots), 0 != rid);
    }

    NewVirtualFile c = fakeRoot.refreshAndFindChild("Users");
    assertNull(c);
    c = fakeRoot.refreshAndFindChild("Users");
    assertNull(c);
    c = fakeRoot.refreshAndFindChild("Windows");
    assertNull(c);
    c = fakeRoot.refreshAndFindChild("Windows");
    assertNull(c);
  }

  public void testFindRootShouldNotBeFooledByRelativePath() throws IOException {
    File tmp = createTempDirectory();
    File x = new File(tmp, "x.jar");
    x.createNewFile();
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vx = lfs.refreshAndFindFileByIoFile(x);
    assertNotNull(vx);
    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);

    PersistentFS fs = PersistentFS.getInstance();

    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    NewVirtualFile root1 = fs.findRoot(path, jfs);

    assertSame(root1, root);
  }

  public void testDeleteSubstRoots() throws IOException, InterruptedException {
    if (!SystemInfo.isWindows) return;

    File tempDirectory = FileUtil.createTempDirectory(getTestName(false), null);
    File substRoot = IoTestUtil.createSubst(tempDirectory.getPath());
    VirtualFile subst = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(substRoot);
    assertNotNull(subst);
    try {
      final File[] children = substRoot.listFiles();
      assertNotNull(children);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);
    PersistentFS fs = PersistentFS.getInstance();

    VirtualFile[] roots = fs.getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      String rootPath = root.getPath();
      String prefix = StringUtil.commonPrefix(rootPath, substRoot.getPath());
      assertEmpty(prefix);
    }
  }
}
