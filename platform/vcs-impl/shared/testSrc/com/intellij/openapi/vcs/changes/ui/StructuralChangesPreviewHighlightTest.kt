// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent

@TestApplication
internal class StructuralChangesPreviewHighlightTest {
  @Test
  fun `dim removes only its own markup`(): Unit = timeoutRunBlocking {
    withEditor("before\nselected {\n  body\n}\nafter") { editor ->
      val range = TextRange(7, editor.document.text.indexOf("\nafter"))
      val unrelated = editor.markupModel.addRangeHighlighter(0, 6, HighlighterLayer.WARNING, TextAttributes(), HighlighterTargetArea.EXACT_RANGE)
      val handle = checkNotNull(highlightStructuralPreview(editor, range))
      val highlighter = editor.markupModel.allHighlighters.single { it !== unrelated }
      assertTrue(highlighter.customRenderer != null)

      Disposer.dispose(handle)

      assertFalse(highlighter.isValid)
      assertEquals(listOf(unrelated), editor.markupModel.allHighlighters.toList())
      unrelated.dispose()
    }
  }

  @Test
  fun `highlight stays in one editor and preserves the caret and selection`(): Unit = timeoutRunBlocking {
    withEditor("before selected after") { editor ->
      val factory = EditorFactory.getInstance()
      val otherEditor = factory.createEditor(editor.document, projectFixture.get())
      try {
        editor.caretModel.moveToOffset(2)
        editor.selectionModel.setSelection(1, 4)
        val scheme = editor.colorsScheme
        val handle = checkNotNull(highlightStructuralPreview(editor, TextRange(7, 15)))
        assertTrue(otherEditor.markupModel.allHighlighters.isEmpty())
        assertEquals(2, editor.caretModel.offset)
        assertEquals(1, editor.selectionModel.selectionStart)
        assertEquals(4, editor.selectionModel.selectionEnd)
        assertSame(scheme, editor.colorsScheme)
        Disposer.dispose(handle)
        assertTrue(editor.markupModel.allHighlighters.isEmpty())
      }
      finally {
        factory.releaseEditor(otherEditor)
      }
    }
  }

  @Test
  fun `empty and stale ranges do not add markup`(): Unit = timeoutRunBlocking {
    withEditor("selected") { editor ->
      assertNull(highlightStructuralPreview(editor, TextRange.EMPTY_RANGE))
      assertNull(highlightStructuralPreview(editor, TextRange(0, editor.document.textLength + 1)))
      assertTrue(editor.markupModel.allHighlighters.isEmpty())
    }
  }

  @Test
  fun `dim preserves the selected text and fades both same-line neighbours`(): Unit = timeoutRunBlocking {
    withEditor("before selected after") { editor ->
      val range = TextRange(7, 15)
      val image = renderDim(editor, range)
      assertClear(image, editor.characterPoint(range.startOffset))
      assertClear(image, editor.characterPoint(range.endOffset - 1))
      assertDimmed(image, editor.characterPoint(0))
      assertDimmed(image, editor.characterPoint(range.endOffset + 1))
    }
  }

  @Test
  fun `whole lines keep the full width of their lines and a blank line`(): Unit = timeoutRunBlocking {
    withEditor("before\nfirst\nlast\nafter\n\nend") { editor ->
      val document = editor.document
      val range = TextRange(document.getLineStartOffset(1), document.getLineEndOffset(2))
      val image = renderDim(editor, range, wholeLines = true)
      assertClear(image, editor.characterPoint(range.startOffset))
      assertClear(image, Point(600, editor.characterPoint(document.getLineStartOffset(2)).y))
      assertDimmed(image, editor.characterPoint(0))
      assertDimmed(image, editor.characterPoint(document.getLineStartOffset(3)))

      val blankLine = TextRange.from(document.getLineStartOffset(4), 0)
      val blankImage = renderDim(editor, blankLine, wholeLines = true)
      assertClear(blankImage, Point(300, editor.characterPoint(blankLine.startOffset).y))
      assertDimmed(blankImage, editor.characterPoint(document.getLineStartOffset(3)))
    }
  }

  @Test
  fun `a null range dims every line`(): Unit = timeoutRunBlocking {
    withEditor("first\nsecond\nthird") { editor ->
      val document = editor.document
      val image = renderDim(editor, null, wholeLines = true)
      for (line in 0 until document.lineCount) {
        assertDimmed(image, editor.characterPoint(document.getLineStartOffset(line)))
      }
    }
  }

  @Test
  fun `the gutter dim covers the line numbers outside the lines of the range`(): Unit = timeoutRunBlocking {
    withEditor("first\nsecond\nthird") { editor ->
      val document = editor.document
      val range = TextRange(document.getLineStartOffset(1), document.getLineEndOffset(1))
      val gutter = editor.gutter as JComponent
      val children = gutter.components.toList()
      Disposer.dispose(checkNotNull(highlightStructuralPreview(editor, range, wholeLines = true)))
      assertEquals(children, gutter.components.toList(), "Without the gutter option, the gutter gets no dim")

      val handle = checkNotNull(highlightStructuralPreview(editor, range, wholeLines = true, gutter = true))
      val dim = gutter.components.single { it !in children }
      assertFalse(dim.contains(5, 5), "The dim takes no mouse events")
      val image = renderGutterDim(editor, dim)
      assertClear(image, Point(5, editor.characterPoint(document.getLineStartOffset(1)).y))
      assertDimmed(image, Point(5, editor.characterPoint(document.getLineStartOffset(0)).y))
      assertDimmed(image, Point(5, editor.characterPoint(document.getLineStartOffset(2)).y))

      Disposer.dispose(handle)
      assertEquals(children, gutter.components.toList(), "The end of the dim removes it from the gutter")
    }
  }

  @Test
  fun `the strength of the dim follows the registry key`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withEditor("first\nsecond") { editor ->
      val document = editor.document
      val range = TextRange(document.getLineStartOffset(1), document.getLineEndOffset(1))
      val point = editor.characterPoint(0)
      val strength = RegistryManager.getInstance().get(StructuralChangesViewSettings.DIM_REGISTRY_KEY)

      strength.setValue(0, disposable)
      assertEquals(SOURCE_COLOR.rgb, renderDim(editor, range, wholeLines = true).getRGB(point.x, point.y), "A strength of 0 paints no dim")

      strength.setValue(100, disposable)
      assertEquals(editor.colorsScheme.defaultBackground.rgb, renderDim(editor, range, wholeLines = true).getRGB(point.x, point.y),
                   "A strength of 100 paints the background")
    }
  }

  @Test
  fun `dim skips bidirectional boundary lines`(): Unit = timeoutRunBlocking {
    val hebrewValue = "\u05E9\u05DC\u05D5\u05DD"
    val cases = listOf(
      "key=$hebrewValue" to "key=$hebrewValue",
      "$hebrewValue key=value" to "key=value",
      "key=value $hebrewValue" to "key=value",
      "key=\n$hebrewValue" to "key=\n$hebrewValue",
    )
    for ((text, selectedText) in cases) {
      withEditor(text) { editor ->
        val range = TextRange.from(text.indexOf(selectedText), selectedText.length)
        assertNull(highlightStructuralPreview(editor, range))
        assertTrue(editor.markupModel.allHighlighters.isEmpty())
      }
    }
  }

  @Test
  fun `dim follows soft wraps and exclusive range edges`(): Unit = timeoutRunBlocking {
    val text = "before " + "selected ".repeat(12) + "after"
    withEditor(text) { editor ->
      assertTrue(EditorTestUtil.configureSoftWraps(editor, 24))
      val wraps = editor.softWrapModel.getSoftWrapsForRange(0, text.length)
      assertTrue(wraps.size >= 2)
      val ranges = listOf(TextRange(7, text.length - 5), TextRange(wraps.first().start, wraps.last().start))
      for (range in ranges) {
        val image = renderDim(editor, range)
        for (offset in range.startOffset until range.endOffset) {
          if (!text[offset].isWhitespace()) assertClear(image, editor.characterPoint(offset))
        }
        assertDimmed(image, editor.characterPoint(0))
        assertDimmed(image, editor.characterPoint(text.lastIndex))
      }
    }
  }

  @Test
  fun `dim keeps a folded element visible`(): Unit = timeoutRunBlocking {
    val text = "before selected {\n  body\n} after"
    withEditor(text) { editor ->
      val foldStart = text.indexOf('{')
      val foldEnd = text.indexOf('}') + 1
      editor.foldingModel.runBatchFoldingOperation {
        checkNotNull(editor.foldingModel.addFoldRegion(foldStart, foldEnd, "{...}")).isExpanded = false
      }
      for (range in listOf(TextRange(7, foldEnd), TextRange(text.indexOf("body"), text.indexOf("body") + 4))) {
        val image = renderDim(editor, range)
        assertClear(image, editor.characterPoint(foldStart))
        assertDimmed(image, editor.characterPoint(0))
        assertDimmed(image, editor.characterPoint(text.lastIndex))
      }
    }
  }

  @Test
  fun `dim clips a long element to a scrolled viewport`(): Unit = timeoutRunBlocking {
    val text = "before\n" + "  selected line\n".repeat(300) + "after"
    withEditor(text) { editor ->
      val range = TextRange(7, text.indexOf("after"))
      val clip = Rectangle(40, editor.lineHeight * 150, 300, editor.lineHeight * 4)
      val image = renderDim(editor, range, clip)
      assertClear(image, Point(1, 1))
      assertClear(image, Point(image.width - 1, image.height - 1))
      val afterClip = Rectangle(0, editor.offsetToXY(range.endOffset).y, 300, editor.lineHeight)
      val afterImage = renderDim(editor, range, afterClip)
      assertDimmed(afterImage, Point(2, editor.lineHeight / 2))
    }
  }

  private suspend fun withEditor(text: String, test: (Editor) -> Unit) {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val highlighter = EditorHighlighterFactory.getInstance()
        .createEditorHighlighter(PlainSyntaxHighlighter(), EditorColorsManager.getInstance().globalScheme)
      val editor = factory.createEditor(factory.createDocument(text), project) as EditorEx
      try {
        editor.highlighter = highlighter
        EditorTestUtil.setEditorVisibleSizeInPixels(editor, 640, 480)
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }

  private fun renderDim(editor: Editor, range: TextRange?, clip: Rectangle? = null, wholeLines: Boolean = false): BufferedImage {
    val bounds = clip ?: Rectangle(0, 0, 640, editor.offsetToXY(editor.document.textLength).y + editor.lineHeight)
    val image = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB)
    val handle = checkNotNull(highlightStructuralPreview(editor, range, wholeLines))
    val highlighter = editor.markupModel.allHighlighters.single { it.customRenderer != null }
    val renderer = checkNotNull(highlighter.customRenderer)
    assertEquals(CustomHighlighterOrder.AFTER_TEXT, renderer.order)
    val graphics = image.createGraphics()
    try {
      graphics.color = SOURCE_COLOR
      graphics.fillRect(0, 0, image.width, image.height)
      graphics.translate(-bounds.x, -bounds.y)
      graphics.clip(bounds)
      renderer.paint(editor, highlighter, graphics)
    }
    finally {
      graphics.dispose()
      Disposer.dispose(handle)
    }
    return image
  }

  /** Paints the gutter part of the dim over an image of the source color. The gutter paints the dim after its line numbers. */
  private fun renderGutterDim(editor: Editor, dim: Component): BufferedImage {
    val image = BufferedImage(20, editor.offsetToXY(editor.document.textLength).y + editor.lineHeight, BufferedImage.TYPE_INT_RGB)
    dim.setSize(image.width, image.height)
    val graphics = image.createGraphics()
    try {
      graphics.color = SOURCE_COLOR
      graphics.fillRect(0, 0, image.width, image.height)
      dim.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
    return image
  }

  private fun Editor.characterPoint(offset: Int): Point = offsetToXY(offset, true, false).apply {
    translate(1, lineHeight / 2)
  }

  private fun assertClear(image: BufferedImage, point: Point) {
    assertEquals(SOURCE_COLOR.rgb, image.getRGB(point.x, point.y), "Keep the selected element visible at $point")
  }

  private fun assertDimmed(image: BufferedImage, point: Point) {
    assertNotEquals(SOURCE_COLOR.rgb, image.getRGB(point.x, point.y), "Dim the text outside the selected element at $point")
  }

  companion object {
    private val projectFixture = projectFixture()
    private val SOURCE_COLOR = Color.MAGENTA
  }
}
