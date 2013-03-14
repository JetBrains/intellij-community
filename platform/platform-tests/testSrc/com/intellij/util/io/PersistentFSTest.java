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

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;

public class PersistentFSTest extends PlatformTestCase {
  @Override
  public void setUp() throws Exception {
    initPlatformLangPrefix();
    super.setUp();
  }

  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    file.createNewFile();

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();

    assertSame(vFile, PersistentFS.getInstance().findFileById(id));
    vFile.delete(this);

    assertNull(PersistentFS.getInstance().findFileById(id));
  }

  public void testListChildrenOfTheRootOfTheRoot() {
    PersistentFS fs = PersistentFS.getInstance();
    VirtualFile fakeRoot = fs.findRoot("", LocalFileSystem.getInstance());
    int users = fs.getId(fakeRoot, "Users", LocalFileSystem.getInstance());
    assertEquals(0, users);
    int win = fs.getId(fakeRoot, "Windows", LocalFileSystem.getInstance());
    assertEquals(0, win);

    VirtualFile[] roots = fs.getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      int rid = fs.getId(fakeRoot, root.getName(), LocalFileSystem.getInstance());
      assertTrue(0 != rid);
    }
  }
}
