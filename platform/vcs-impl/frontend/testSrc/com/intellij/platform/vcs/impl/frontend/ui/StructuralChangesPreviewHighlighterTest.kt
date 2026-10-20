// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ui.StructuralChangesPreviewHighlighter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent

@TestApplication
internal class StructuralChangesPreviewHighlighterTest {
  @Test
  fun `service rejects a stale stamp and another file`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      withContext(Dispatchers.EDT) {
        val stamp = fixture.editor.document.modificationStamp
        assertNull(service.highlight(fixture.file, RANGE, stamp - 1))
        assertNull(service.highlight(LightVirtualFile("other.txt", "before selected after"), RANGE, stamp))
      }
    }
  }

  @Test
  fun `dim rejects a permanent tab`(): Unit = timeoutRunBlocking {
    withEditor(preview = false) { fixture ->
      withContext(Dispatchers.EDT) {
        assertFalse(checkNotNull(fixture.manager.getComposite(fixture.file)).isPreview)
        assertNull(service.highlight(fixture.file, RANGE, fixture.editor.document.modificationStamp))
      }
    }
  }

  @Test
  fun `dim accepts a preview tab and clears when its owner disposes it`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      withContext(Dispatchers.EDT) {
        assertTrue(checkNotNull(fixture.manager.getComposite(fixture.file)).isPreview)
        val (handle, markup) = highlight(fixture)
        assertTrue(markup.customRenderer != null)

        Disposer.dispose(handle)

        assertFalse(markup.isValid)
      }
    }
  }

  @Test
  fun `making a preview tab permanent removes the highlight`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      val cleared = CompletableDeferred<Unit>()
      val markup = withContext(Dispatchers.EDT) {
        val composite = checkNotNull(fixture.manager.getComposite(fixture.file))
        assertTrue(composite.isPreview)
        val (handle, markup) = highlight(fixture)
        Disposer.register(handle) { cleared.complete(Unit) }

        composite.isPreview = false

        markup
      }
      cleared.await()
      withContext(Dispatchers.EDT) {
        assertSame(fixture.editor, fixture.manager.selectedTextEditor)
        assertFalse(markup.isValid)
      }
    }
  }

  @Test
  fun `caret selection and Escape remove the highlight`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      withContext(Dispatchers.EDT) {
        val (_, caretMarkup) = highlight(fixture)
        fixture.editor.caretModel.moveToOffset(1)
        assertFalse(caretMarkup.isValid)

        val (_, selectionMarkup) = highlight(fixture)
        fixture.editor.selectionModel.setSelection(2, 4)
        assertFalse(selectionMarkup.isValid)

        val component = fixture.editor.contentComponent
        val originalListeners = component.keyListeners.toSet()
        val (_, keyMarkup) = highlight(fixture)
        val event = KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
        component.keyListeners.filter { it !in originalListeners }.forEach { it.keyPressed(event) }
        assertFalse(keyMarkup.isValid)
        assertTrue(component.keyListeners.toSet() == originalListeners)
      }
    }
  }

  @Test
  fun `editing removes the highlight`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      val (_, markup) = withContext(Dispatchers.EDT) { highlight(fixture) }
      withContext(Dispatchers.EDT) {
        edtWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction { fixture.editor.document.insertString(0, "changed ") }
        }
        assertFalse(markup.isValid)
      }
    }
  }

  @Test
  fun `closing the editor removes the highlight`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      withContext(Dispatchers.EDT) {
        val (_, markup) = highlight(fixture)
        fixture.manager.closeFile(fixture.file)
        assertFalse(markup.isValid)
      }
    }
  }

  @Test
  fun `selecting another editor removes the highlight`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      val cleared = CompletableDeferred<Unit>()
      val (_, markup) = withContext(Dispatchers.EDT) {
        highlight(fixture).also { (handle, _) -> Disposer.register(handle) { cleared.complete(Unit) } }
      }
      fixture.manager.openFile(LightVirtualFile("other.txt", "other"), options = FileEditorOpenOptions())
      cleared.await()
      withContext(Dispatchers.EDT) {
        assertFalse(markup.isValid)
      }
    }
  }

  @Test
  fun `background disposal removes markup on EDT`(): Unit = timeoutRunBlocking {
    withEditor { fixture ->
      val (handle, markup) = withContext(Dispatchers.EDT) { highlight(fixture) }
      withContext(Dispatchers.Default) { Disposer.dispose(handle) }
      withContext(Dispatchers.EDT) { assertFalse(markup.isValid) }
    }
  }

  private val service: StructuralChangesPreviewHighlighter
    get() = serviceFixture.get()

  private fun highlight(fixture: Fixture): Pair<Disposable, RangeHighlighter> {
    val original = fixture.editor.markupModel.allHighlighters.toSet()
    val handle = checkNotNull(service.highlight(fixture.file, RANGE, fixture.editor.document.modificationStamp))
    val markup = fixture.editor.markupModel.allHighlighters.single { it !in original }
    return handle to markup
  }

  private suspend fun withEditor(preview: Boolean = true, test: suspend (Fixture) -> Unit) {
    val manager = fileEditorManagerFixture.get()
    val file = LightVirtualFile("selected.txt", "before selected after")
    try {
      val composite = manager.openFile(file, options = FileEditorOpenOptions(usePreviewTab = preview, forcePreviewTab = preview))
      val editor = withContext(Dispatchers.EDT) {
        composite.allEditors.filterIsInstance<TextEditor>().single().editor.also { EditorTestUtil.waitForLoading(it) }
      }
      test(Fixture(file, editor, manager))
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) { manager.closeAllFiles() }
    }
  }

  private data class Fixture(val file: VirtualFile, val editor: Editor, val manager: FileEditorManagerImpl)

  companion object {
    private val RANGE = TextRange(7, 15)
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
    private val serviceFixture = testFixture {
      val service = StructuralChangesPreviewHighlighterImpl(projectFixture.init())
      initialized(service) {
        withContext(Dispatchers.EDT) { Disposer.dispose(service) }
      }
    }
  }
}
