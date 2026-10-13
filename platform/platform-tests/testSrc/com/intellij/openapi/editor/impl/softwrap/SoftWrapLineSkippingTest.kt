// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The soft wrap engine skips a line that surely fits into the visible width.
 * These tests compare its soft wraps with the soft wraps of an editor where the engine measures each line.
 */
@TestApplication
class SoftWrapLineSkippingTest {
  @Test
  fun `skipped lines give the same soft wraps as measured lines`(): Unit = timeoutRunBlocking {
    val (skipped, measured) = withContext(Dispatchers.EDT) {
      withSoftWrappedEditor(TEXT, measureEachLine = false) { softWraps(it) } to
        withSoftWrappedEditor(TEXT, measureEachLine = true) { softWraps(it) }
    }

    assertThat(skipped).isNotEmpty()
    assertThat(skipped).isEqualTo(measured)
  }

  @Test
  fun `the prefix text counts in the width of the first line`(): Unit = timeoutRunBlocking {
    val firstLine = "a short first line that fits alone"
    val text = firstLine + "\n" + TEXT
    val (skipped, measured) = withContext(Dispatchers.EDT) {
      withSoftWrappedEditor(text, measureEachLine = false, prefix = PREFIX) { softWraps(it) } to
        withSoftWrappedEditor(text, measureEachLine = true, prefix = PREFIX) { softWraps(it) }
    }

    // Without the prefix, the first line fits. With the prefix, it gets a soft wrap.
    assertThat(measured.map { it.substringBefore(':').toInt() }).anyMatch { it < firstLine.length }
    assertThat(skipped).isEqualTo(measured)
  }

  @Test
  fun `a last line without a line break gives the same soft wraps as measured lines`(): Unit = timeoutRunBlocking {
    val text = TEXT + "a last line without a line break, which is wider than the visible width of the editor and wraps"
    val (skipped, measured) = withContext(Dispatchers.EDT) {
      withSoftWrappedEditor(text, measureEachLine = false) { softWraps(it) } to
        withSoftWrappedEditor(text, measureEachLine = true) { softWraps(it) }
    }

    assertThat(skipped).isEqualTo(measured)
  }

  @Test
  fun `document changes give the same soft wraps as measured lines`(): Unit = timeoutRunBlocking {
    val (skipped, measured) = withContext(Dispatchers.EDT) {
      val (changedText, softWraps) = withSoftWrappedEditor(TEXT, measureEachLine = false) { editor ->
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(null) {
          document.insertString(document.getLineEndOffset(0), " and a tail that makes the first line wrap at least once or twice")
        }
        // The change is in the last visual line of a wrapped line, so the recalculation starts at a soft wrap.
        WriteCommandAction.runWriteCommandAction(null) {
          document.insertString(document.getLineEndOffset(1), ", and the line grows by some more words at its end")
        }
        WriteCommandAction.runWriteCommandAction(null) {
          document.deleteString(document.getLineStartOffset(3), document.getLineEndOffset(3))
        }
        WriteCommandAction.runWriteCommandAction(null) {
          document.insertString(document.getLineStartOffset(5), "\tx\tx\tx\tx\tx\tx\tx\tx\tx\tx\tx\tx")
        }
        document.text to softWraps(editor)
      }
      softWraps to withSoftWrappedEditor(changedText, measureEachLine = true) { softWraps(it) }
    }

    assertThat(skipped).isEqualTo(measured)
  }

  private fun <T> withSoftWrappedEditor(text: String, measureEachLine: Boolean, prefix: String? = null, action: (EditorImpl) -> T): T {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(editorFactory.createDocument(text)) as EditorImpl
    try {
      if (prefix != null) {
        editor.setPrefixTextAndAttributes(prefix, TextAttributes())
      }
      if (measureEachLine) {
        // An inlay in the range turns the skipping off. The inlay is on an empty last line or on the short first line, so it moves nothing.
        editor.inlayModel.addInlineElement(if (text.endsWith('\n')) text.length else 0, OnePixelRenderer)
      }
      editor.settings.isUseSoftWraps = true
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 400, 1000)
      return action(editor)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun softWraps(editor: EditorImpl): List<String> {
    editor.softWrapModel.prepareToMapping()
    return editor.softWrapModel.registeredSoftWraps.map { "${it.start}:${it.indentInColumns}:${it.indentInPixels}" }
  }

  private object OnePixelRenderer : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 1
  }

  private companion object {
    const val PREFIX: String = "a prefix of thirty characters>"

    val TEXT: String = buildString {
      append("short line\n")
      append("a long line of plain text that is wider than the visible width of the editor, so it gets a soft wrap\n")
      append("\n")
      append("\tindented\twith\ttabs\n")
      append("\t\ta long line with tabs\tthat is wider than the visible width\tof the editor\tand gets soft wraps\n")
      append("короткая строка\n")
      append("длинная строка с кириллицей, которая шире видимой области редактора и поэтому получает перенос\n")
      append("arrows \u2192 and ideographs \u6f22\u5b57 in a line that is long enough to get a soft wrap in the narrow editor\n")
      repeat(20) { append("line $it\n") }
      append("the last long line of plain text that is wider than the visible width of the editor and wraps\n")
    }
  }
}
