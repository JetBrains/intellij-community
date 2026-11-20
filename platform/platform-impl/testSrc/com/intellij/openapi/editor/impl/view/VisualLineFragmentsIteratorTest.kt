// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.geom.Point2D

private const val CHAR_WIDTH = 10
private const val LINE_HEIGHT = 10
private const val DESCENT = 2
private const val THREE_LINES = "abcdefghij\nklmn\nopqrstuvwx"
private val LINE_WITH_INLAY_AND_FOLD_REGION = listOf(Kind.TEXT, Kind.INLAY, Kind.TEXT, Kind.FOLD_REGION, Kind.TEXT)

/**
 * Checks the fragments of one visual line: text, inlays and collapsed fold regions, with their offsets, columns and x.
 * Each character of [MockFontLayoutService] is [CHAR_WIDTH] pixels wide.
 */
@TestApplication
internal class VisualLineFragmentsIteratorTest {
  @Test
  fun `a line without inlays and fold regions is one text fragment`(): Unit = withEditor("abc") { editor ->
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..3, columns = 0..3, x = 0f..30f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `an inlay splits the text`(): Unit = withEditor("ab") { editor ->
    editor.addInlay(1, 5)
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..1, columns = 0..1, x = 0f..10f),
        FragmentState(Kind.INLAY, offsets = 1..1, columns = 1..2, x = 10f..15f),
        FragmentState(Kind.TEXT, offsets = 1..2, columns = 2..3, x = 15f..25f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `an inlay at the line end is the last fragment`(): Unit = withEditor("ab\ncd") { editor ->
    editor.addInlay(2, 5)
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..2, columns = 0..2, x = 0f..20f),
        FragmentState(Kind.INLAY, offsets = 2..2, columns = 2..3, x = 20f..25f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `a collapsed fold region is one fragment with the placeholder width`(): Unit = withEditor("abcd") { editor ->
    editor.addCollapsedFoldRegion(1, 3, "...")
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..1, columns = 0..1, x = 0f..10f),
        FragmentState(Kind.FOLD_REGION, offsets = 1..3, columns = 1..4, x = 10f..40f),
        FragmentState(Kind.TEXT, offsets = 3..4, columns = 4..5, x = 40f..50f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `adjacent fold regions give no empty text fragment between them`(): Unit = withEditor("abcd") { editor ->
    editor.addCollapsedFoldRegion(1, 2, ".")
    editor.addCollapsedFoldRegion(2, 3, ".")
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..1, columns = 0..1, x = 0f..10f),
        FragmentState(Kind.FOLD_REGION, offsets = 1..2, columns = 1..2, x = 10f..20f),
        FragmentState(Kind.FOLD_REGION, offsets = 2..3, columns = 2..3, x = 20f..30f),
        FragmentState(Kind.TEXT, offsets = 3..4, columns = 3..4, x = 30f..40f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `a fold region hides the inlay at its start, not at its end`(): Unit = withEditor("abcd") { editor ->
    editor.addInlay(1, 5)
    editor.addInlay(3, 5)
    editor.addCollapsedFoldRegion(1, 3, ".")
    assertEquals(
      listOf(
        FragmentState(Kind.TEXT, offsets = 0..1, columns = 0..1, x = 0f..10f),
        FragmentState(Kind.FOLD_REGION, offsets = 1..3, columns = 1..2, x = 10f..20f),
        FragmentState(Kind.INLAY, offsets = 3..3, columns = 2..3, x = 20f..25f),
        FragmentState(Kind.TEXT, offsets = 3..4, columns = 3..4, x = 25f..35f),
      ),
      editor.fragmentsAt(0),
    )
  }

  @Test
  fun `a fold region across lines ends on the next logical line`(): Unit = withEditor("ab\ncd") { editor ->
    editor.addCollapsedFoldRegion(1, 4, ".")
    val iterable = VisualLineFragmentsIterator.create(editor.view, editor.document.documentText, 0, false, false)
    val logicalLines = iterable.map { it.startLogicalLine..it.endLogicalLine }
    assertEquals(listOf(0..0, 0..1, 1..1), logicalLines)
    assertEquals(
      editor.fragmentsAt(0),
      editor.fragmentsAt(4),
      "an offset after the fold region gives the same visual line",
    )
  }

  @Test
  fun `a soft wrap starts a visual line with the wrap indent`(): Unit = withEditor("abcdefgh") { editor ->
    EditorTestUtil.configureSoftWraps(editor, 4)
    val wrap = editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength).first()
    val wrappedLine = editor.fragmentsAt(wrap.start)
    assertEquals(wrap.start, wrappedLine.first().offsets.first)
    assertEquals(wrap.indentInColumns, wrappedLine.first().columns.first)
    assertEquals(wrap.indentInPixels.toFloat(), wrappedLine.first().x.start)
    assertEquals(wrap.start, editor.fragmentsAt(0).last().offsets.last, "the first visual line ends at the wrap")
    assertEquals(
      editor.fragmentsAt(0),
      editor.fragmentsAt(wrap.start, beforeSoftWrap = true),
      "beforeSoftWrap selects the first visual line",
    )
  }

  @Test
  fun `an empty document has no fragments`(): Unit = withEditor("") { editor ->
    assertEquals(emptyList<FragmentState>(), editor.fragmentsAt(0))
  }

  @Test
  fun `a custom fold region is the last fragment of its visual line`(): Unit = withEditor("ab\ncd") { editor ->
    EditorTestUtil.addCustomFoldRegion(editor, 0, 0)
    val fragments = editor.fragmentsAt(0)
    assertEquals(listOf(Kind.FOLD_REGION), fragments.map { it.kind })
    assertEquals(0, fragments.single().offsets.first)
  }

  @Test
  fun `next fails after the last fragment`(): Unit = withEditor("abc") { editor ->
    val iterable = VisualLineFragmentsIterator.create(editor.view, editor.document.documentText, 0, false, false)
    val iterator = iterable.iterator()
    assertEquals(Kind.TEXT, iterator.next().kind())
    assertFalse(iterator.hasNext())
    assertThrows<NoSuchElementException> {
      val _ = iterator.next()
    }
  }

  @Test
  fun `the offset and the visual line iterator give the same fragments`(): Unit = withEditor(THREE_LINES) { editor ->
    editor.addCollapsedFoldRegion(2, 4, ".")
    editor.addCollapsedFoldRegion(13, 18, "..")
    editor.addInlay(6, 5)
    editor.addInlay(20, 5)
    EditorTestUtil.configureSoftWraps(editor, 5)
    val visualLines = VisualLinesIterator(editor, 0)
    var visualLineCount = 0
    while (!visualLines.atEnd()) {
      val byVisualLine = VisualLineFragmentsIterator.create(
        editor.view,
        editor.document.documentText,
        visualLines,
        null,
        false,
      ).map { it.state(editor.originX()) }
      val byOffset = editor.fragmentsAt(visualLines.visualLineStartOffset)
      assertEquals(byVisualLine, byOffset, "visual line ${visualLines.visualLine}")
      visualLineCount++
      visualLines.advance()
    }
    assertTrue(visualLineCount > 3, "the text must wrap: $visualLineCount visual lines")
  }

  @Test
  fun `fragment coordinates agree in a left-aligned editor`(): Unit = withEditor("ab cd") { editor ->
    editor.addInlay(1, 20)
    editor.addCollapsedFoldRegion(3, 4, "...")
    assertEquals(LINE_WITH_INLAY_AND_FOLD_REGION, editor.checkFragmentCoordinates(false))
  }

  @Test
  fun `fragment coordinates agree in a right-aligned editor`(): Unit = withEditor("ab cd") { editor ->
    editor.addInlay(1, 20)
    editor.addCollapsedFoldRegion(3, 4, "...")
    editor.alignRight()
    assertTrue(editor.offsetToPoint2D(0, false, false).x > 500, "the line must be shifted to the right")
    assertEquals(LINE_WITH_INLAY_AND_FOLD_REGION, editor.checkFragmentCoordinates(true))
  }

  @Test
  fun `a right-aligned editor maps the positions of an inlay`(): Unit = withEditor("ab") { editor ->
    editor.addInlay(1, 20)
    editor.alignRight()
    val inlayStartX = editor.offsetToPoint2D(1, false, false).x
    val y = editor.visualLineToY(0) + 1.0
    assertTrue(inlayStartX > 500, "the line must be shifted to the right: $inlayStartX")
    assertEquals(inlayStartX, editor.visualPositionToPoint2D(VisualPosition(0, 1, true)).x)
    assertEquals(inlayStartX + 20, editor.visualPositionToPoint2D(VisualPosition(0, 2, false)).x)
    assertEquals(VisualPosition(0, 1, false), editor.xyToVisualPosition(Point2D.Double(inlayStartX + 5, y)))
    assertEquals(VisualPosition(0, 2, true), editor.xyToVisualPosition(Point2D.Double(inlayStartX + 15, y)))
  }

  @Test
  fun `a right-aligned editor maps the positions of a fold region`(): Unit = withEditor("abcd") { editor ->
    editor.addCollapsedFoldRegion(1, 3, "...")
    editor.alignRight()
    val foldRegionStartX = editor.offsetToPoint2D(1, false, false).x
    val y = editor.visualLineToY(0) + 1.0
    assertTrue(foldRegionStartX > 500, "the line must be shifted to the right: $foldRegionStartX")
    assertEquals(foldRegionStartX, editor.offsetToPoint2D(2, false, false).x, "an offset inside the fold region")
    assertEquals(foldRegionStartX + 10, editor.visualPositionToPoint2D(VisualPosition(0, 2, false)).x)
    assertEquals(2, editor.xyToVisualPosition(Point2D.Double(foldRegionStartX + 12, y)).column)
  }

  @Test
  fun `the position before a soft wrap uses the shift of its line`(): Unit = withEditor("abcd efg") { editor ->
    EditorTestUtil.configureSoftWraps(editor, 5)
    editor.alignRight()
    val wrap = editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength).first().start
    val column = editor.offsetToVisualPosition(wrap, false, true).column
    val firstLineStartX = editor.visualPositionToPoint2D(VisualPosition(0, 0)).x
    val secondLineStartX = editor.visualPositionToPoint2D(VisualPosition(1, 0)).x
    assertNotEquals(firstLineStartX, secondLineStartX, "the visual lines must have different shifts")
    val firstLineEndX = editor.visualPositionToPoint2D(VisualPosition(0, column)).x
    assertEquals(firstLineEndX, editor.offsetToPoint2D(wrap, false, true).x)
  }

  /**
   * Runs [test] on the EDT with a new editor of [text]. The editor uses [MockFontLayoutService].
   */
  private fun withEditor(text: String, test: (EditorImpl) -> Unit): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument(text)) as EditorImpl
      try {
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
        FontLayoutService.setInstance(null)
      }
    }
  }
}

private enum class Kind {
  TEXT,
  INLAY,
  FOLD_REGION,
}

/**
 * A copy of the values of one fragment.
 * The iterator returns one [VisualLineFragment] object for all fragments of a kind, so a test keeps copies.
 * The x range is relative to the line start x of a left-aligned editor.
 */
private data class FragmentState(
  val kind: Kind,
  val offsets: IntRange,
  val columns: IntRange,
  val x: ClosedFloatingPointRange<Float>,
)

private fun EditorImpl.fragmentsAt(offset: Int, beforeSoftWrap: Boolean = false): List<FragmentState> {
  val iterable = VisualLineFragmentsIterator.create(view, document.documentText, offset, beforeSoftWrap, false)
  return iterable.map { it.state(originX()) }
}

/**
 * Iterates the visual line at offset 0 and checks each fragment against its own bounds:
 * the start and the end columns map to the start and the end x, and back.
 * Returns the fragment kinds.
 */
private fun EditorImpl.checkFragmentCoordinates(align: Boolean): List<Kind> {
  val kinds = ArrayList<Kind>()
  for (fragment in VisualLineFragmentsIterator.create(view, document.documentText, 0, false, align)) {
    val kind = fragment.kind()
    val name = "$kind ${fragment.startOffset}..${fragment.endOffset}"
    assertEquals(fragment.startX, fragment.visualColumnToX(fragment.startVisualColumn), "$name: x of the start column")
    assertEquals(fragment.endX, fragment.visualColumnToX(fragment.endVisualColumn), "$name: x of the end column")
    val startXColumn = fragment.xToVisualColumn(fragment.startX).column
    val endXColumn = fragment.xToVisualColumn(fragment.endX).column
    assertEquals(fragment.startVisualColumn, startXColumn, "$name: column of the start x")
    assertEquals(fragment.endVisualColumn, endXColumn, "$name: column of the end x")
    if (kind != Kind.INLAY) {
      assertEquals(fragment.startX, fragment.offsetToX(fragment.startOffset), "$name: x of the start offset")
      assertEquals(fragment.endX, fragment.offsetToX(fragment.endOffset), "$name: x of the end offset")
    }
    if (kind == Kind.TEXT) {
      val endX = fragment.offsetToX(fragment.startX, fragment.startOffset, fragment.endOffset)
      assertEquals(fragment.endX, endX, "$name: x of the end offset from the start x")
    }
    kinds.add(kind)
  }
  return kinds
}

private fun EditorImpl.originX(): Float {
  return view.insets.left.toFloat()
}

private fun EditorImpl.alignRight() {
  setHorizontalTextAlignment(EditorImpl.TEXT_ALIGNMENT_RIGHT)
  contentComponent.setSize(1000, 100)
}

private fun EditorImpl.addInlay(offset: Int, widthInPixels: Int) {
  EditorTestUtil.addInlay(this, offset, false, widthInPixels)
}

private fun EditorImpl.addCollapsedFoldRegion(startOffset: Int, endOffset: Int, placeholder: String) {
  foldingModel.runBatchFoldingOperation {
    val region = checkNotNull(foldingModel.addFoldRegion(startOffset, endOffset, placeholder))
    region.isExpanded = false
  }
}

private fun VisualLineFragment.kind(): Kind {
  if (foldRegion != null) {
    return Kind.FOLD_REGION
  } else if (inlay != null) {
    return Kind.INLAY
  } else {
    return Kind.TEXT
  }
}

private fun VisualLineFragment.state(originX: Float): FragmentState {
  return FragmentState(
    kind = kind(),
    offsets = startOffset..endOffset,
    columns = startVisualColumn..endVisualColumn,
    x = startX - originX..endX - originX,
  )
}
