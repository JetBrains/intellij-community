// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import git4idea.test.add
import git4idea.test.checkout
import git4idea.test.commit
import git4idea.test.mv
import junit.framework.TestCase

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
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.MODIFY)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Modify-Delete conflict.
   */
  fun testConflictMD() {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.DELETE)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Delete-Modify conflict.
   */
  fun testConflictDM() {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.MODIFY)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
  }

  /**
   * Create a file with conflicting content.
   */
  fun testConflictCC() {
    modifyFileInBranches("z.txt", FileAction.CREATE, FileAction.CREATE)
    val zfile = projectRoot.findChild("z.txt")
    assertChanges(zfile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }

  fun testConflictRD() {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.DELETE)
    val newfile = projectRoot.findChild("a.txt_master_new") // renamed in master
    assertChanges(newfile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }

  fun testConflictDR() {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.RENAME)
    val newFile = projectRoot.findChild("a.txt_feature_new") // deleted in master, renamed in feature
    assertChanges(newFile!!, FileStatus.MERGED_WITH_CONFLICTS)
  }
  
  private fun modifyFileInBranches(filename: String, masterAction: FileAction, featureAction: FileAction) {
    git("checkout -b feature")
    performActionOnFileAndRecordToIndex(filename, "feature", featureAction)
    repo.commit("commit to feature")
    repo.checkout("master")
    refresh()
    performActionOnFileAndRecordToIndex(filename, "master", masterAction)
    repo.commit("commit to master")
    git("merge feature", true)
    refresh()
  }

  private fun performActionOnFileAndRecordToIndex(filename: String, branchName: String, action: FileAction) {
    val file = projectRoot.findChild(filename)
    if (action != FileAction.CREATE) { // file doesn't exist yet
      TestCase.assertNotNull("VirtualFile is null: " + filename, file)
    }
    when (action) {
      FileAction.CREATE -> {
        val f = Executor.touch(filename, "initial content in branch " + branchName)
        val createdFile = VfsUtil.findFileByIoFile(f, true)
        dirty(createdFile)
        repo.add(filename)
      }
      FileAction.MODIFY -> {
        Executor.overwrite(VfsUtilCore.virtualToIoFile(file!!), "new content in branch " + branchName)
        dirty(file)
        repo.add(filename)
      }
      FileAction.DELETE -> {
        dirty(file)
        git("rm " + filename)
      }
      FileAction.RENAME -> {
        val newName = filename + "_" + branchName.replace("\\s".toRegex(), "_") + "_new"
        dirty(file)
        repo.mv(filename, newName)
        projectRoot.refresh(false, true)
        dirty(projectRoot.findChild(newName))
      }
    }
  }

  private enum class FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }
}
