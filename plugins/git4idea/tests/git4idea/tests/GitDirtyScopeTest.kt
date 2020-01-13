// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.BaseChangeListsTest
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.DocumentUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.GitSingleRepoTest
import git4idea.test.commit

class GitDirtyScopeTest : GitSingleRepoTest() {
  private lateinit var dirtyScopeManager: VcsDirtyScopeManager
  private lateinit var fileDocumentManager: FileDocumentManager
  private lateinit var undoManager: UndoManager

  override fun setUp() {
    super.setUp()

    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
    fileDocumentManager = FileDocumentManager.getInstance()
    undoManager = UndoManager.getInstance(project)
  }

  fun testRevertingUnsavedChanges() {
    val file = repo.root.createFile("file.txt", "initial")
    git("add .")
    commit("initial")
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    editDocument(file, "new content")
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }

    editDocument(file, "initial")
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }

    saveDocument(file) // Usually, should be triggered by LST
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
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }

    undoChanges(file)
    changeListManager.waitUntilRefreshed()

    assertNoChanges()

    editDocument(file, "new content")
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
    changeListManager.waitUntilRefreshed()

    assertChanges {
      modified("file.txt")
    }
    assertFalse(isDirtyPath(file))

    editDocument(file, "new better content")

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

    emptyBulkChange(file)

    assertTrue(isDirtyPath(file))
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

  private fun emptyBulkChange(file: VirtualFile) {
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = file.document
        DocumentUtil.executeInBulk(document, true) {
          // do nothing
        }
      }
    }
  }

  private fun isDirtyPath(file: VirtualFile): Boolean {
    val filePath = VcsUtil.getFilePath(file)
    val dirtyPaths = dirtyScopeManager.whatFilesDirty(listOf(filePath))
    return dirtyPaths.contains(filePath)
  }

  private val VirtualFile.document: Document get() = fileDocumentManager.getDocument(this)!!
}