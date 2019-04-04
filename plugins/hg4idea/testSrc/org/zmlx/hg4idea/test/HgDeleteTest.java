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

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.fail;

public class HgDeleteTest extends HgSingleUserTest {

  @Test
  public void testDeleteUnmodifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    deleteFileInCommand(file);
    verify(runHgOnProjectRepo("status"), HgTestOutputParser.removed("a.txt"));
  }

  @Test
  public void testDeleteUnversionedFile() throws Exception {
    VirtualFile file = makeFile(new File(myWorkingCopyDir.getPath(), "a.txt"));
    verify(runHgOnProjectRepo("status"), HgTestOutputParser.unknown("a.txt"));
    deleteFileInCommand(file);
    Assert.assertFalse(file.exists());
  }

  @Test
  public void testDeleteNewFile() {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    deleteFileInCommand(file);
    Assert.assertFalse(file.exists());
  }

  @Test
  public void testDeleteModifiedFile() throws Exception {
    VirtualFile file = createFileInCommand("a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    VcsTestUtil.editFileInCommand(myProject, file, "even newer content");
    verify(runHgOnProjectRepo("status"), HgTestOutputParser.modified("a.txt"));
    deleteFileInCommand(file);
    verify(runHgOnProjectRepo("status"), HgTestOutputParser.removed("a.txt"));
  }

  @Test
  public void testDeleteDirWithFiles() throws Exception {
    VirtualFile parent = createDirInCommand(myWorkingCopyDir, "com");
    createFileInCommand(parent, "a.txt", "new file content");
    runHgOnProjectRepo("commit", "-m", "added file");
    deleteFileInCommand(parent);
    verify(runHgOnProjectRepo("status"), HgTestOutputParser.removed("com", "a.txt"));
  }

  /**
   * When deleting a file which was newly added to repository, this file shouldn't be prompted for removal from the repository.
   * 1. Create a file and add it to the repository.
   * 2. Remove the file from disk.
   * 3. File shouldn't be prompted for removal from repository.
   */
  @Test
  public void testNewlyAddedFileShouldNotBePromptedForRemoval() {
    showConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile vf = createFileInCommand("a.txt", null);
    final HgMockVcsHelper helper = registerMockVcsHelper();
    helper.addListener(new VcsHelperListener() {
      @Override
      public void dialogInvoked() {
        fail("No dialog should be invoked, because newly added file should be silently removed from the repository");
      }
    });
    deleteFileInCommand(vf);
  }

  /**
   * A file is also considered to be newly added, if it has a history, but the last action was removal of that file.
   * 1. Create a file, add it to the repository and commit.
   * 2. Delete the file and commit it.
   * 3. Create the file again and add it to the repository.
   * 4. Delete the file.
   * 5. File shouldn't be prompted for removal from repository.
   */
  @Test
  public void testJustDeletedAndThenAddedFileShouldNotBePromptedForRemoval() {
    VirtualFile vf = createFileInCommand("a.txt", null);
    myChangeListManager.commitFiles(vf);
    deleteFileInCommand(vf);
    myChangeListManager.commitFiles(vf);

    showConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
    vf = createFileInCommand("a.txt", null);
    final HgMockVcsHelper helper = registerMockVcsHelper();
    helper.addListener(new VcsHelperListener() {
      @Override
      public void dialogInvoked() {
        fail("No dialog should be invoked, because newly added file should be silently removed from the repository");
      }
    });
    VcsTestUtil.deleteFileInCommand(myProject, vf);
  }

}
