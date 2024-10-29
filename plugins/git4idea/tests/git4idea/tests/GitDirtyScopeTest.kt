// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.BaseChangeListsTest
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.VcsDirtyScopeVfsListener
import com.intellij.openapi.vcs.impl.FileStatusManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.DocumentUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.GitSingleRepoTest
import git4idea.test.commit
import org.junit.Assume.assumeFalse

class GitDirtyScopeTest : GitSingleRepoTest() {
  private lateinit var dirtyScopeManager: VcsDirtyScopeManager
  private lateinit var fileDocumentManager: FileDocumentManager
  private lateinit var fileStatusManager: FileStatusManagerImpl
  private lateinit var undoManager: UndoManager

  override fun setUp() {
    super.setUp()

    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
    fileDocumentManager = FileDocumentManager.getInstance()
    undoManager = UndoManager.getInstance(project)
    fileStatusManager = FileStatusManager.getInstance(project) as FileStatusManagerImpl
  }

  fun testRevertingUnsavedChanges() {
    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    editDocument(file, "new content")
    fileStatusManager.waitFor()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }

    editDocument(file, "initial")
    fileStatusManager.waitFor()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }

    saveDocument(file) // Usually, should be triggered by LST
    fileStatusManager.waitFor()
    changeListManager.waitUntilRefreshed()

    assertNoChanges()
  }

  fun testUndoingUnsavedChanges() {
    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    editDocument(file, "new content")
    fileStatusManager.waitFor() // processModifiedDocument -> fileDirty
    changeListManager.waitUntilRefreshed() // refresh from git

    assertChanges {
      modified("file.txt")
    }

    undoChanges(file)
    fileStatusManager.waitFor() // processModifiedDocument -> fileDirty
    changeListManager.waitUntilRefreshed() // refresh from git

    assertNoChanges()

    editDocument(file, "new content")
    fileStatusManager.waitFor()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }
  }

  fun testTypingDoesNotMarkDirty() {
    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    editDocument(file, "new content")
    fileStatusManager.waitFor()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }
    assertFalse(isDirtyPath(file))

    editDocument(file, "new better content")
    fileStatusManager.waitFor()

    assertFalse(isDirtyPath(file))
  }

  fun testEmptyBulkModeDoesNotMarkDirty() {
    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    editDocument(file, "new content")
    saveDocument(file)
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }
    assertFalse(isDirtyPath(file))

    writeAction {
      DocumentUtil.executeInBulk(file.document) {
        // do nothing
      }
    }

    assertFalse(isDirtyPath(file))
  }

  fun testCaseOnlyRename() {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive)

    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")

    editDocument(file, "initial")
    saveDocument(file)

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    changeListManager.forceStopInTestMode()

    writeAction {
      val parent = file.parent
      file.delete(this)
      parent.createFile("FILE.txt", "initial")
    }
    git("add .")
    project.service<VcsDirtyScopeVfsListener>().waitForAsyncTaskCompletion()

    changeListManager.forceGoInTestMode()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      rename("file.txt", "FILE.txt")
    }
    assertFalse(isDirtyPath(file))
  }

  fun testCaseOnlyDirectoryRename() {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive)

    val dir = repo.root.createDir("dir")
    val file = dir.createFile("file.txt", "initial")
    git("add .")
    commit("initial")

    editDocument(file, "initial")
    saveDocument(file)

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    changeListManager.forceStopInTestMode()

    writeAction {
      dir.delete(this)
      val newDir = repo.root.createDir("DIR")
      newDir.createFile("file.txt", "initial")
    }
    dirtyScopeManager.dirDirtyRecursively(VcsUtil.getFilePath(repo.root, "DHq")) // hash code collisions
    dirtyScopeManager.dirDirtyRecursively(VcsUtil.getFilePath(repo.root, "djS"))
    git("add .")
    project.service<VcsDirtyScopeVfsListener>().waitForAsyncTaskCompletion()

    changeListManager.forceGoInTestMode()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      rename("dir/file.txt", "DIR/file.txt")
    }
    assertFalse(isDirtyPath(file))
  }


  private fun editDocument(file: VirtualFile, newContent: String) {
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = file.document
        document.replaceString(0, document.textLength, newContent)
      }
    }
  }

  private fun undoChanges(file: VirtualFile) {
    runInEdtAndWait {
      val fileEditor = BaseChangeListsTest.createMockFileEditor(file.document)
      undoManager.undo(fileEditor)
    }
  }

  private fun saveDocument(file: VirtualFile) {
    runInEdtAndWait {
      fileDocumentManager.saveDocument(file.document)
    }
  }

  private fun writeAction(task: () -> Unit) {
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        task()
      }
    }
  }

  private fun isDirtyPath(file: VirtualFile): Boolean {
    return isDirtyPath(VcsUtil.getFilePath(file))
  }

  private fun isDirtyPath(filePath: FilePath): Boolean {
    val dirtyPaths = dirtyScopeManager.whatFilesDirty(listOf(filePath))
    return dirtyPaths.contains(filePath)
  }

  private val VirtualFile.document: Document get() = fileDocumentManager.getDocument(this)!!
}