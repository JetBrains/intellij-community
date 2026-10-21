// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

/**
 * Checks that the editor review model uses the shared comment navigation.
 * The navigation itself is covered by `CodeReviewCommentNavigatorTest`.
 */
@TestApplication
internal class GitLabMergeRequestEditorNavigationTest {
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  private val project get() = projectFixture.get()

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `comment in the current file moves the caret in the current editor`() {
    checkNavigation(sameFile = true)
  }

  @Test
  fun `comment in another file opens the file and requests the thread focus`() {
    checkNavigation(sameFile = false)
  }

  private fun checkNavigation(sameFile: Boolean): Unit = timeoutRunBlocking {
    val currentFile = LightVirtualFile("current.txt", "first\nsecond\nthird\n")
    val targetFile = if (sameFile) currentFile else LightVirtualFile("target.txt", "first\nsecond\nthird\n")
    val currentChange = changeFor(currentFile)
    val targetChange = if (sameFile) currentChange else changeFor(targetFile)
    val editorManager = mockk<FileEditorManager>(relaxed = true) {
      every { runWhenLoaded(any(), any()) } answers { secondArg<Runnable>().run() }
      every { requestOpenEditor(any(), any()) } returns CompletableFuture()
    }
    project.replaceService(FileEditorManager::class.java, editorManager, disposableFixture.get())
    val fileVm = mockk<GitLabMergeRequestEditorReviewFileViewModel> {
      every { change } returns currentChange
      every { changedRanges } returns emptyList()
      every { discussions } returns MutableStateFlow(ComputedResult.loading())
      every { draftNotes } returns MutableStateFlow(ComputedResult.loading())
      every { newDiscussions } returns MutableStateFlow(emptyList())
      every { linesWithDiscussions } returns MutableStateFlow(emptySet())
      every { linesWithNewDiscussions } returns MutableStateFlow(emptySet())
      every { canComment } returns MutableStateFlow(true)
      every { lookupNextComment("current", any()) } returns "target"
      every { getThreadPosition("target") } returns (targetChange to 2)
      every { requestThreadFocus(any()) } just Runs
    }

    try {
      withEditor(currentFile) { editor ->
        val controller = GitLabMergeRequestEditorReviewController(project, this)
        val model = controller.createReviewModel(this, mockk(), fileVm, editor)
        withContext(Dispatchers.EDT) {
          model.setPostReviewChanges(emptyList())
          model.gotoNextComment("current")
        }

        if (sameFile) {
          assertEquals(2, withContext(Dispatchers.EDT) { editor.caretModel.logicalPosition.line })
        }
        verify(exactly = if (sameFile) 0 else 1) { editorManager.requestOpenEditor(any(), any()) }
        verify { fileVm.requestThreadFocus("target") }
      }
    }
    finally {
      coroutineContext.cancelChildren()
    }
  }

  private fun changeFor(file: VirtualFile): RefComparisonChange = mockk {
    every { filePathAfter } returns mockk {
      every { virtualFile } returns file
    }
  }

  private suspend fun withEditor(file: VirtualFile, action: suspend (Editor) -> Unit) {
    val editor = withContext(Dispatchers.EDT) {
      val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
      EditorFactory.getInstance().createEditor(document)
    }
    try {
      action(editor)
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }
}
