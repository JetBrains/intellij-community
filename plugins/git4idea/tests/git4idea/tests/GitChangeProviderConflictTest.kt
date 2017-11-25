// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.vcs.FileStatus

class GitChangeProviderConflictTest : GitChangeProviderTest() {

  /**
   * "modify-modify" merge conflict.
   * 1. Create a file and commit it.
   * 2. Create new branch and switch to it.
   * 3. Edit the file in that branch and commit.
   * 4. Switch to master, conflictly edit the file and commit.
   * 5. Merge the branch on master.
   * Merge conflict "modify-modify" happens.
   */
  fun testConflictMM() {
    modifyFileInBranches("a.txt", GitChangeProviderTest.FileAction.MODIFY, GitChangeProviderTest.FileAction.MODIFY)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Modify-Delete conflict.
   */
  fun testConflictMD() {
    modifyFileInBranches("a.txt", GitChangeProviderTest.FileAction.MODIFY, GitChangeProviderTest.FileAction.DELETE)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Delete-Modify conflict.
   */
  fun testConflictDM() {
    modifyFileInBranches("a.txt", GitChangeProviderTest.FileAction.DELETE, GitChangeProviderTest.FileAction.MODIFY)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Create a file with conflicting content.
   */
  fun testConflictCC() {
    modifyFileInBranches("z.txt", GitChangeProviderTest.FileAction.CREATE, GitChangeProviderTest.FileAction.CREATE)
    val zfile = projectRoot.findChild("z.txt")
    assertChanges(zfile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }

  fun testConflictRD() {
    modifyFileInBranches("a.txt", GitChangeProviderTest.FileAction.RENAME, GitChangeProviderTest.FileAction.DELETE)
    val newfile = projectRoot.findChild("a.txt_master_new") // renamed in master
    assertChanges(newfile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }

  fun testConflictDR() {
    modifyFileInBranches("a.txt", GitChangeProviderTest.FileAction.DELETE, GitChangeProviderTest.FileAction.RENAME)
    val newFile = projectRoot.findChild("a.txt_feature_new") // deleted in master, renamed in feature
    assertChanges(newFile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }

}
