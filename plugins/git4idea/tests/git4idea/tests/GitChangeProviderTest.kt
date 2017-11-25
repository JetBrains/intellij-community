// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsTestUtil.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.MockChangeListManagerGate
import com.intellij.testFramework.vcs.MockChangelistBuilder
import com.intellij.testFramework.vcs.MockDirtyScope
import com.intellij.vcsUtil.VcsUtil
import git4idea.status.GitChangeProvider
import git4idea.test.*
import junit.framework.TestCase
import java.io.File
import java.util.*

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 */
abstract class GitChangeProviderTest : GitSingleRepoTest() {

  protected lateinit var changeProvider: GitChangeProvider
  protected lateinit var dirtyScope: VcsModifiableDirtyScope
  protected lateinit var subDir: VirtualFile

  protected lateinit var atxt: VirtualFile
  protected lateinit var btxt: VirtualFile
  protected lateinit var dir_ctxt: VirtualFile
  protected lateinit var subdir_dtxt: VirtualFile

  override fun setUp() {
    super.setUp()
    changeProvider = vcs.changeProvider as GitChangeProvider

    createFileStructure(projectRoot, "a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt")
    addCommit("initial")

    atxt = getVirtualFile("a.txt")
    btxt = getVirtualFile("b.txt")
    dir_ctxt = getVirtualFile("dir/c.txt")
    subdir_dtxt = getVirtualFile("dir/subdir/d.txt")
    subDir = projectRoot.findChild("dir")!!

    dirtyScope = MockDirtyScope(myProject, vcs)

    cd(projectPath)
  }

  override fun makeInitialCommit() = false

  private fun getVirtualFile(relativePath: String) = VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!

  protected fun modifyFileInBranches(filename: String, masterAction: FileAction, featureAction: FileAction) {
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

  protected enum class FileAction {
    CREATE, MODIFY, DELETE, RENAME
  }

  private fun performActionOnFileAndRecordToIndex(filename: String, branchName: String, action: FileAction) {
    val file = projectRoot.findChild(filename)
    if (action != FileAction.CREATE) { // file doesn't exist yet
      TestCase.assertNotNull("VirtualFile is null: " + filename, file)
    }
    when (action) {
      GitChangeProviderTest.FileAction.CREATE -> {
        val f = touch(filename, "initial content in branch " + branchName)
        val createdFile = VfsUtil.findFileByIoFile(f, true)
        dirty(createdFile)
        repo.add(filename)
      }
      GitChangeProviderTest.FileAction.MODIFY -> {

        overwrite(VfsUtilCore.virtualToIoFile(file!!), "new content in branch " + branchName)
        dirty(file)
        repo.add(filename)
      }
      GitChangeProviderTest.FileAction.DELETE -> {
        dirty(file)
        git("rm " + filename)
      }
      GitChangeProviderTest.FileAction.RENAME -> {
        val newName = filename + "_" + branchName.replace("\\s".toRegex(), "_") + "_new"
        dirty(file)
        repo.mv(filename, newName)
        projectRoot.refresh(false, true)
        dirty(projectRoot.findChild(newName))
      }
      else -> {
      }
    }
  }

  /**
   * Checks that the given files have respective statuses in the change list retrieved from myChangesProvider.
   * Pass null in the fileStatuses array to indicate that proper file has not changed.
   */
  protected fun assertChanges(virtualFiles: List<VirtualFile>, fileStatuses: List<FileStatus?>) {
    val result = getChanges(virtualFiles)
    for (i in virtualFiles.indices) {
      val fp = VcsUtil.getFilePath(virtualFiles[i])
      val status = fileStatuses[i]
      if (status == null) {
        TestCase.assertFalse("File [" + tos(fp) + " shouldn't be in the changelist, but it was.", result.containsKey(fp))
        continue
      }
      TestCase.assertTrue("File [" + tos(fp) + "] didn't change. Changes: " + tos(result), result.containsKey(fp))
      TestCase.assertEquals("File statuses don't match for file [" + tos(fp) + "]", result[fp]!!.getFileStatus(), status)
    }
  }

  protected fun assertChanges(virtualFile: VirtualFile, fileStatus: FileStatus) {
    assertChanges(listOf(virtualFile), listOf(fileStatus))
  }

  /**
   * Marks the given files dirty in myDirtyScope, gets changes from myChangeProvider and groups the changes in the map.
   * Assumes that only one change for a file has happened.
   */
  private fun getChanges(changedFiles: List<VirtualFile>): Map<FilePath, Change> {
    val changedPaths = changedFiles.map { VcsUtil.getFilePath(it) }

    // get changes
    val builder = MockChangelistBuilder()
    changeProvider.getChanges(dirtyScope, builder, EmptyProgressIndicator(),
                              MockChangeListManagerGate(ChangeListManager.getInstance(myProject)))
    val changes = builder.changes

    // get changes for files
    val result = HashMap<FilePath, Change>()
    for (change in changes) {
      val file = change.virtualFile
      var filePath: FilePath? = null
      if (file == null) { // if a file was deleted, just find the reference in the original list of files and use it.
        val path = change.beforeRevision!!.file.path
        for (fp in changedPaths) {
          if (FileUtil.pathsEqual(fp.path, path)) {
            filePath = fp
            break
          }
        }
      }
      else {
        filePath = VcsUtil.getFilePath(file)
      }
      result.put(filePath!!, change)
    }
    return result
  }

  protected fun create(parent: VirtualFile, name: String): VirtualFile {
    val file = parent.createFile(name, "content" + Math.random())
    dirty(file)
    return file
  }

  protected fun edit(file: VirtualFile, content: String) {
    editFileInCommand(myProject, file, content)
    dirty(file)
  }

  protected fun moveFile(file: VirtualFile, newParent: VirtualFile) {
    dirty(file)
    moveFileInCommand(myProject, file, newParent)
    dirty(file)
  }

  protected fun copy(file: VirtualFile, newParent: VirtualFile): VirtualFile {
    dirty(file)
    val newFile = copyFileInCommand(myProject, file, newParent, file.name)
    dirty(newFile)
    return newFile
  }

  protected fun deleteFile(file: VirtualFile) {
    dirty(file)
    deleteFileInCommand(myProject, file)
  }

  protected fun dirty(file: VirtualFile?) {
    dirtyScope.addDirtyFile(VcsUtil.getFilePath(file!!))
  }

  protected fun tos(fp: FilePath): String? {
    return FileUtil.getRelativePath(File(projectPath), fp.ioFile)
  }

  protected fun tos(change: Change): String {
    when (change.type) {
      Change.Type.NEW -> return "A: " + tos(change.afterRevision)!!
      Change.Type.DELETED -> return "D: " + tos(change.beforeRevision)!!
      Change.Type.MOVED -> return "M: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
      Change.Type.MODIFICATION -> return "M: " + tos(change.afterRevision)!!
      else -> return "~: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
    }
  }

  protected fun tos(revision: ContentRevision?): String? {
    return tos(revision!!.file)
  }

  protected fun tos(changes: Map<FilePath, Change>): String {
    val stringBuilder = StringBuilder("[")
    for (change in changes.values) {
      stringBuilder.append(tos(change)).append(", ")
    }
    stringBuilder.append("]")
    return stringBuilder.toString()
  }

}
