// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsTestUtil.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.MockChangeListManagerGate
import com.intellij.testFramework.vcs.MockChangelistBuilder
import com.intellij.testFramework.vcs.MockDirtyScope
import com.intellij.vcsUtil.VcsUtil
import git4idea.status.GitChangeProvider
import git4idea.test.GitSingleRepoTest
import git4idea.test.addCommit
import git4idea.test.createFileStructure
import java.io.File
import java.util.*

/**
 * Tests GitChangeProvider functionality. Scenario is the same for all tests:
 * 1. Modifies files on disk (creates, edits, deletes, etc.)
 * 2. Manually adds them to a dirty scope.
 * 3. Calls ChangeProvider.getChanges() and checks that the changes are there.
 */
abstract class GitChangeProviderTest : GitSingleRepoTest() {

  private lateinit var changeProvider: GitChangeProvider
  protected lateinit var dirtyScope: VcsModifiableDirtyScope
  private lateinit var subDir: VirtualFile

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
        assertFalse("File [" + tos(fp) + " shouldn't be in the changelist, but it was.", result.containsKey(fp))
        continue
      }
      assertTrue("File [${tos(fp)}] didn't change. Changes: [${result.values.joinToString(",") { tos(it) }}]", result.containsKey(fp))
      assertEquals("File statuses don't match for file [${tos(fp)}]", result[fp]!!.fileStatus, status)
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

  private fun tos(fp: FilePath) = FileUtil.getRelativePath(File(projectPath), fp.ioFile)

  private fun tos(change: Change) = when (change.type) {
    Change.Type.NEW -> "A: " + tos(change.afterRevision)!!
    Change.Type.DELETED -> "D: " + tos(change.beforeRevision)!!
    Change.Type.MOVED -> "M: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
    Change.Type.MODIFICATION -> "M: " + tos(change.afterRevision)!!
    else -> "~: " + tos(change.beforeRevision) + " -> " + tos(change.afterRevision)
  }

  private fun tos(revision: ContentRevision?) = tos(revision!!.file)

}
