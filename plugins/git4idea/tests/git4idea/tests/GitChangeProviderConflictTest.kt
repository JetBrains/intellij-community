// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import git4idea.repo.GitConflict.ConflictSide
import git4idea.repo.GitConflict.Status
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
    assertManagerConflicts(Conflict("a.txt", Status.MODIFIED, Status.MODIFIED))
  }

  /**
   * Modify-Delete conflict.
   */
  fun testConflictMD() {
    modifyFileInBranches("a.txt", FileAction.MODIFY, FileAction.DELETE)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
    assertManagerConflicts(Conflict("a.txt", Status.MODIFIED, Status.DELETED))
  }

  /**
   * Delete-Modify conflict.
   */
  fun testConflictDM() {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.MODIFY)
    assertChanges(atxt, FileStatus.MERGED_WITH_CONFLICTS)
    assertManagerConflicts(Conflict("a.txt", Status.DELETED, Status.MODIFIED))
  }

  /**
   * Create a file with conflicting content.
   */
  fun testConflictCC() {
    modifyFileInBranches("z.txt", FileAction.CREATE, FileAction.CREATE)
    val zfile = projectRoot.findChild("z.txt")
    assertChanges(zfile!!, FileStatus.MERGED_WITH_CONFLICTS)
    assertManagerConflicts(Conflict("z.txt", Status.ADDED, Status.ADDED))
  }

  fun testConflictRD() {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.DELETE)
    val newfile = projectRoot.findChild("a.txt_master_new") // renamed in master
    assertChanges(newfile!!, FileStatus.MERGED_WITH_CONFLICTS)
    assertManagerConflicts(Conflict("a.txt_master_new", Status.ADDED, Status.MODIFIED))
  }

  fun testConflictDR() {
    modifyFileInBranches("a.txt", FileAction.DELETE, FileAction.RENAME)
    val newFile = projectRoot.findChild("a.txt_feature_new") // deleted in master, renamed in feature
    assertChanges(newFile!!, FileStatus.MERGED_WITH_CONFLICTS)
    assertManagerConflicts(Conflict("a.txt_feature_new", Status.MODIFIED, Status.ADDED))
  }

  fun testConflictRR() {
    modifyFileInBranches("a.txt", FileAction.RENAME, FileAction.RENAME)
    val newMasterFile = projectRoot.findChild("a.txt_master_new")!!
    val newFeatureFile = projectRoot.findChild("a.txt_feature_new")!!
    assertChanges(listOf(newMasterFile, newFeatureFile), listOf(FileStatus.MERGED_WITH_CONFLICTS, FileStatus.MERGED_WITH_CONFLICTS))
    assertManagerConflicts(Conflict("a.txt_master_new", Status.ADDED, Status.MODIFIED),
                           Conflict("a.txt_feature_new", Status.MODIFIED, Status.ADDED),
                           Conflict("a.txt", Status.DELETED, Status.DELETED, false))
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

  private fun assertManagerConflicts(vararg expectedConflicts: Conflict) {
    updateChangeListManager()

    val actualConflicts = repo.conflictsHolder.conflicts.map {
      Conflict(it.filePath.name,
               it.getStatus(ConflictSide.OURS),
               it.getStatus(ConflictSide.THEIRS))
    }
    assertSameElements(actualConflicts, expectedConflicts.toList())

    val actualLocalChangesConflicts = changeListManager.allChanges
      .filter { it.fileStatus == FileStatus.MERGED_WITH_CONFLICTS }
      .map { ChangesUtil.getFilePath(it).name }
    val expectedLocalChangesConflicts = expectedConflicts.filter { it.visibleInLocalChanges }.map { it.name }
    assertSameElements(actualLocalChangesConflicts, expectedLocalChangesConflicts)
  }

  private enum class FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private class Conflict(val name: String,
                         val ourStatus: Status,
                         val theirsStatus: Status,
                         val visibleInLocalChanges: Boolean = true) {
    override fun hashCode(): Int = name.hashCode()
    override fun equals(other: Any?): Boolean = other is Conflict &&
                                                name == other.name &&
                                                ourStatus == other.ourStatus &&
                                                theirsStatus == other.theirsStatus

    override fun toString(): String = "$name - $ourStatus - $theirsStatus"
  }
}
