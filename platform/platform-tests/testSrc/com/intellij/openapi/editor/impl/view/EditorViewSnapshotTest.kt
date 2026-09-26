// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.impl.EditorViewAccessor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.assertThrows
import java.awt.Font
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EditorViewSnapshotTest : AbstractEditorTest() {
  private val editorEx: EditorEx get() = editor as EditorEx

  fun `test concurrent reads see consistent metrics while the view reinitializes`() {
    initText("a\tb\nlonger line of text\n")
    val view = EditorViewAccessor.getView(editor)
    val stop = AtomicBoolean()
    val started = CountDownLatch(1)
    val failure = AtomicReference<Throwable>()
    val reader = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        while (!stop.get()) {
          val lineHeight = view.lineHeight
          val descent = view.descent
          assertEquals(
            "The ascent must match the line height and the descent of one snapshot",
            lineHeight - descent, view.ascent,
          )
          assertTrue(
            "The caret height must be positive",
            view.caretHeight > 0,
          )
          started.countDown()
        }
      }
      catch (e: Throwable) {
        failure.set(e)
      } finally {
        started.countDown()
      }
    }
    try {
      assertTrue(
        "The reader made no read",
        started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
      )
      repeat(REINIT_COUNT) {
        editorEx.reinitSettings()
      }
      UIUtil.dispatchAllInvocationEvents()
    } finally {
      stop.set(true)
      reader.get()
    }
    failure.get()?.let {
      throw AssertionError("A concurrent reader saw an inconsistent snapshot", it)
    }
  }

  fun `test caret height follows the full line height cursor setting`() {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    editor.settings.isFullLineHeightCursor = true
    assertEquals(
      "A full line height caret is exactly the line height",
      view.lineHeight,
      view.caretHeight,
    )
    editor.settings.isFullLineHeightCursor = false
    assertEquals(
      "A default caret adds both overhangs",
      view.lineHeight + view.topOverhang + view.bottomOverhang,
      view.caretHeight,
    )
  }

  fun `test the caret repaint metrics drop the top overhang for a full line height caret`() {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    editor.settings.isFullLineHeightCursor = true
    assertEquals(
      "A full line height caret starts at the line top, so it overhangs nothing",
      0,
      EditorViewAccessor.getCaretTopOverhang(editor),
    )
    editor.settings.isFullLineHeightCursor = false
    assertEquals(
      "A default caret starts at the top overhang of the font",
      view.topOverhang,
      EditorViewAccessor.getCaretTopOverhang(editor),
    )
  }

  fun `test a prefix with a text and no attributes is rejected`() {
    initText("text")
    val failure = assertThrows<IllegalArgumentException> {
      editorEx.setPrefixTextAndAttributes("prompt> ", null)
    }
    assertTrue(
      "The message must name the text, but it was '${failure.message}'",
      failure.message!!.contains("prompt> "),
    )
  }

  fun `test a prefix without a text needs no attributes`() {
    initText("text")
    editorEx.setPrefixTextAndAttributes(null, null)
    assertEquals(0, editorEx.prefixTextWidthInPixels)
    editorEx.setPrefixTextAndAttributes("", null)
    assertEquals(0, editorEx.prefixTextWidthInPixels)
  }

  fun `test a rejected prefix keeps the previous one`() {
    initText("text")
    val attributes = TextAttributes(null, null, null, null, Font.PLAIN)
    editorEx.setPrefixTextAndAttributes("prompt> ", attributes)
    val width = editorEx.prefixTextWidthInPixels
    assertTrue(
      "The prefix must have a width, but it was $width",
      width > 0,
    )
    assertThrows<IllegalArgumentException> {
      editorEx.setPrefixTextAndAttributes("other> ", null)
    }
    assertEquals(
      "A rejected prefix leaves the previous one in place",
      width,
      editorEx.prefixTextWidthInPixels,
    )
  }

  companion object {
    private const val REINIT_COUNT = 200
    private const val TIMEOUT_SECONDS = 30L
  }
}
