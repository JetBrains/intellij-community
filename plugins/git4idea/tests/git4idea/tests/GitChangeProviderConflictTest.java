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
package git4idea.tests;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;

public class GitChangeProviderConflictTest extends GitChangeProviderTest {

  /**
   * "modify-modify" merge conflict.
   * 1. Create a file and commit it.
   * 2. Create new branch and switch to it.
   * 3. Edit the file in that branch and commit.
   * 4. Switch to master, conflictly edit the file and commit.
   * 5. Merge the branch on master.
   * Merge conflict "modify-modify" happens.
   */
  public void testConflictMM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.MODIFY);
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Modify-Delete conflict.
   */
  public void testConflictMD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.DELETE);
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Delete-Modify conflict.
   */
  public void testConflictDM() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.MODIFY);
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS);
  }

  /**
   * Create a file with conflicting content.
   */
  public void testConflictCC() throws Exception {
    modifyFileInBranches("z.txt", FileAction.CREATE, FileAction.CREATE);
    VirtualFile zfile = myProjectRoot.findChild("z.txt");
    assertChanges(zfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  public void testConflictRD() throws Exception {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.DELETE);
    VirtualFile newfile = myProjectRoot.findChild("a.txt_master_new"); // renamed in master
    assertChanges(newfile, FileStatus.MERGED_WITH_CONFLICTS);
  }

  public void testConflictDR() throws Exception {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.RENAME);
    VirtualFile newFile = myProjectRoot.findChild("a.txt_feature_new"); // deleted in master, renamed in feature
    assertChanges(newFile, FileStatus.MERGED_WITH_CONFLICTS);
  }

}
