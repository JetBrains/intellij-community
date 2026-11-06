// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.seconds

/**
 * The result of an asynchronous formatting service is applied after the command that requested it, and must still be
 * undone and redone as part of the undo history.
 */
@TestApplication
@SystemProperty("intellij.async.formatting.ignoreHeadless", "true")
class AsyncDocumentFormattingUndoTest {
  companion object {
    private val project = projectFixture(openAfterCreation = true)
  }

  private val psiFileFixture = project.moduleFixture().sourceRootFixture().psiFileFixture("format.txt", "abc")
  private val psiFile by psiFileFixture
  private val editor by psiFileFixture.editorFixture()

  @BeforeEach
  fun registerService(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(FormattingService.EP_NAME, listOf(UppercaseFormattingService(), CoreFormattingService()), disposable)
  }

  // Undo and Redo leave the document uncommitted; the editor's pending history update must not outlive the editor
  @AfterEach
  fun commitDocuments(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      PsiDocumentManager.getInstance(psiFile.project).commitAllDocuments()
    }
  }

  @Test
  fun `undo reverts an asynchronous reformat and redo applies it again`(): Unit = timeoutRunBlocking {
    reformatInCommand("Reformat Code")
    awaitText("ABC")
    withContext(Dispatchers.EDT) {
      undo()
      assertEquals("abc", text())
      assertTrue(UndoManager.getInstance(psiFile.project).isRedoAvailable(textEditor()), "the reformat can be redone")
      redo()
      assertEquals("ABC", text())
      assertEquals("Undo Reformat Code", undoActionName())
    }
  }

  @Test
  fun `undo of an asynchronous reformat keeps the change before it`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(psiFile.project, "Edit", null, { editor.document.insertString(0, "x") })
    }
    reformatInCommand("Reformat Code")
    awaitText("XABC")
    withContext(Dispatchers.EDT) {
      undo()
      assertEquals("xabc", text())
      redo()
      assertEquals("XABC", text())
      undo()
      undo()
      assertEquals("abc", text())
    }
  }

  @Test
  fun `asynchronous result of a command that changes the document is undone with the change`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(psiFile.project, "Generate", null, {
        editor.document.insertString(0, "y")
        PsiDocumentManager.getInstance(psiFile.project).commitDocument(editor.document)
        CodeStyleManager.getInstance(psiFile.project).reformat(psiFile)
      })
    }
    awaitText("YABC")
    withContext(Dispatchers.EDT) {
      assertEquals("Undo Generate", undoActionName())
      undo()
      assertEquals("abc", text())
      redo()
      assertEquals("YABC", text())
    }
  }

  private suspend fun reformatInCommand(name: String) = withContext(Dispatchers.EDT) {
    PsiDocumentManager.getInstance(psiFile.project).commitDocument(editor.document)
    WriteCommandAction.runWriteCommandAction(psiFile.project, name, null, {
      CodeStyleManager.getInstance(psiFile.project).reformat(psiFile)
    })
  }

  private suspend fun awaitText(expected: String) {
    withTimeoutOrNull(10.seconds) {
      while (withContext(Dispatchers.EDT) { text() } != expected) {
        delay(10)
      }
    } ?: run {
      val actual = withContext(Dispatchers.EDT) { text() }
      fail { "The formatted text did not arrive: $actual" }
    }
  }

  private fun text(): String = editor.document.text

  private fun textEditor() = TextEditorProvider.getInstance().getTextEditor(editor)

  private fun undo() = UndoManager.getInstance(psiFile.project).undo(textEditor())

  private fun redo() = UndoManager.getInstance(psiFile.project).redo(textEditor())

  private fun undoActionName(): String =
    UndoManager.getInstance(psiFile.project).getUndoActionNameAndDescription(textEditor()).first.replace("_", "")
}

private class UppercaseFormattingService : AsyncDocumentFormattingService() {
  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask = object : FormattingTask {
    override fun run() {
      formattingRequest.onTextReady(formattingRequest.documentText.uppercase())
    }

    override fun cancel(): Boolean = true
  }

  override fun getNotificationGroupId(): String = "Uppercase"
  override fun getName(): String = "Uppercase"
  override fun getFeatures(): Set<FormattingService.Feature> = setOf(FormattingService.Feature.FORMAT_FRAGMENTS)
  override fun canFormat(file: PsiFile): Boolean = file.name == "format.txt"
}
