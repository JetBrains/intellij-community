// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.DocumentUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertChanges
import git4idea.test.assertNoChanges
import git4idea.test.commit
import git4idea.test.createDir
import git4idea.test.createFile
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitDirtyScopeTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var dirtyScopeManager: VcsDirtyScopeManager
  private lateinit var fileDocumentManager: FileDocumentManager
  private lateinit var fileStatusManager: FileStatusManagerImpl
  private lateinit var undoManager: UndoManager

  @BeforeEach
  fun setUp() {
    with(context) {
      dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
      fileDocumentManager = FileDocumentManager.getInstance()
      undoManager = UndoManager.getInstance(project)
      fileStatusManager = FileStatusManager.getInstance(project) as FileStatusManagerImpl
    }
  }

  @Test
  fun `test reverting unsaved changes`(): Unit = with(context) {
    val file = createFile(repo.root, "file.txt", "initial")
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

  @Test
  fun `test undoing unsaved changes`(): Unit = with(context) {
    val file = createFile(repo.root, "file.txt", "initial")
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

  @Test
  fun `test typing does not mark dirty`(): Unit = with(context) {
    val file = createFile(repo.root, "file.txt", "initial")
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
    assertThat(isDirtyPath(file)).isFalse()

    editDocument(file, "new better content")
    fileStatusManager.waitFor()

    assertThat(isDirtyPath(file)).isFalse()
  }

  @Test
  fun `test empty bulk mode does not mark dirty`(): Unit = with(context) {
    val file = createFile(repo.root, "file.txt", "initial")
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
    assertThat(isDirtyPath(file)).isFalse()

    writeAction {
      DocumentUtil.executeInBulk(file.document) {
        // do nothing
      }
    }

    assertThat(isDirtyPath(file)).isFalse()
  }

  @Test
  fun `test case only rename`(): Unit = with(context) {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive)

    val file = createFile(repo.root, "file.txt", "initial")
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
      createFile(parent, "FILE.txt", "initial")
    }
    git("add .")
    project.service<VcsDirtyScopeVfsListener>().waitForAsyncTaskCompletion()

    changeListManager.forceGoInTestMode()
    changeListManager.waitUntilRefreshed()

    assertChanges {
      rename("file.txt", "FILE.txt")
    }
    assertThat(isDirtyPath(file)).isFalse()
  }

  @Test
  fun `test case only directory rename`(): Unit = with(context) {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive)

    val dir = createDir(repo.root, "dir")
    val file = createFile(dir, "file.txt", "initial")
    git("add .")
    commit("initial")

    editDocument(file, "initial")
    saveDocument(file)

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()

    changeListManager.forceStopInTestMode()

    writeAction {
      dir.delete(this)
      val newDir = createDir(repo.root, "DIR")
      createFile(newDir, "file.txt", "initial")
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
    assertThat(isDirtyPath(file)).isFalse()
  }

  private fun GitSingleRepoContext.editDocument(file: VirtualFile, newContent: String) {
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

  private fun GitSingleRepoContext.writeAction(task: () -> Unit) {
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        task()
      }
    }
  }

  private fun isDirtyPath(file: VirtualFile): Boolean = isDirtyPath(VcsUtil.getFilePath(file))

  private fun isDirtyPath(filePath: FilePath): Boolean {
    return dirtyScopeManager.whatFilesDirty(listOf(filePath)).contains(filePath)
  }

  private val VirtualFile.document: Document get() = fileDocumentManager.getDocument(this)!!
}
