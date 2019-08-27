// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.test;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.File;

import static com.intellij.openapi.vcs.Executor.overwrite;

public class HgRenameTest extends HgSingleUserTest {

  @Test
  public void testRenameUnmodifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    myChangeListManager.ensureUpToDate();
    renameFileInCommand(file, "b.txt");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.added("b.txt"), HgTestOutputParser.removed("a.txt"));
  }

  @Test
  public void testRenameModifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    myChangeListManager.ensureUpToDate();
    overwrite(VfsUtilCore.virtualToIoFile(file), "modified new file content");
    verifyStatus(HgTestOutputParser.modified("a.txt"));
    renameFileInCommand(file, "b.txt");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.added("b.txt"), HgTestOutputParser.removed("a.txt"));
  }

  @Test
  public void testRenameNewFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    renameFileInCommand(file, "b.txt");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.added("b.txt"));
  }

  @Test
  public void testRenameRenamedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    myChangeListManager.ensureUpToDate();
    renameFileInCommand(file, "b.txt");
    myChangeListManager.ensureUpToDate();
    renameFileInCommand(file, "c.txt");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.added("c.txt"), HgTestOutputParser.removed("a.txt"));
  }

  @Test
  public void testRenameVersionedFolder() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    myChangeListManager.ensureUpToDate();
    runHgOnProjectRepo("commit", "-m", "added file");
    myChangeListManager.ensureUpToDate();
    renameFileInCommand(parent, "org");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.added("org", "a.txt"), HgTestOutputParser.removed("com", "a.txt"));
  }

  @Test
  public void testRenameUnversionedFolder() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");

    File unversionedFile = new File(parent.getPath(), "a.txt");
    makeFile(unversionedFile);
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.unknown("com", "a.txt"));
    renameFileInCommand(parent, "org");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.unknown("org", "a.txt"));
  }

  @Test
  public void testRenameUnversionedFile() throws Exception {
    File unversionedFile = new File(myWorkingCopyDir.getPath(), "a.txt");
    VirtualFile file = makeFile(unversionedFile);
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.unknown("a.txt"));
    renameFileInCommand(file, "b.txt");
    myChangeListManager.ensureUpToDate();
    verifyStatus(HgTestOutputParser.unknown("b.txt"));
  }
}
