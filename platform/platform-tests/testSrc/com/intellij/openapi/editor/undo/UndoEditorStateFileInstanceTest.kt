// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * The VFS can return several equal instances of one file. An editor state must apply to an editor of any of them.
 */
@TestApplication
internal class UndoEditorStateFileInstanceTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
  }

  private val file = sourceRoot.virtualFileFixture("file.txt", "")

  @AfterEach
  fun dropHistory() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      undoManager(project.get()).dropHistoryInTests()
    }
  }

  @Test
  fun `undo restores the editor state in an editor of another instance of the file after a GC`() {
    val document = timeoutRunBlocking(context = Dispatchers.EDT) {
      val document = EditorFactory.getInstance().createDocument("")
      typeInEditorOfAnotherInstance(document)
      undoManager(project.get()).flushCurrentCommandMerger()
      document
    }
    GCUtil.tryGcSoftlyReachableObjects()
    val reopened = StateRecordingEditor(file.get(), document, 7)
    val text = timeoutRunBlocking(context = Dispatchers.EDT) {
      val undoManager = undoManager(project.get())
      undoManager.withCurrentEditor(reopened) {
        undoManager.undo(reopened)
      }
      document.text
    }
    assertEquals(listOf(CaretState(2)), reopened.appliedStates())
    assertEquals(" ", text)
  }

  private fun typeInEditorOfAnotherInstance(document: Document) {
    val instance = checkNotNull(file.get().parent.findChild(file.get().name))
    assertNotSame(file.get(), instance)
    val editor = StateRecordingEditor(instance, document, 1)
    undoManager(project.get()).withCurrentEditor(editor) {
      typeSpace(project.get(), document) {
        editor.moveCaret(2)
      }
    }
  }
}
