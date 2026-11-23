// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.testFramework.AbstractMockGlyphVector
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.util.function.IntToDoubleFunction

private const val CELL_WIDTH = 10
private const val LINE_HEIGHT = 10
private const val DESCENT = 2
private const val WRAP_COLUMNS = 10

private const val CJK = 0x4E2D // a wide char
private const val GRIN = 0x1F600 // a narrow code point whose glyph is wider than its cell
private const val MAN = 0x1F468
private const val WOMAN = 0x1F469
private const val ZWJ = 0x200D // the zero width joiner
private const val ACUTE = 0x0301 // a mark that the font composes with its base
private const val DOT_BELOW = 0x0323 // a mark that the font keeps as a glyph of its own
private const val ALEF = 0x05D0
private const val BET = 0x05D1
private const val LETTER_E = 0x65

private val WIDE_CODE_POINTS = setOf(CJK, MAN, WOMAN)
private val JOINING_CODE_POINTS = setOf(ZWJ, ACUTE, DOT_BELOW)

/**
 * The lines of the test document. Only the first line gets the inlay of the measured soft wrap case.
 */
private val LINES = listOf(
  GridLine("the inlay line", "zz"),
  GridLine("ASCII", "abcdefghijklmnopqrst"),
  GridLine("a wide char", ("a" + text(CJK) + "b").repeat(5)),
  GridLine("a cluster of several code points", ("a" + text(MAN, ZWJ, WOMAN) + "b").repeat(3)),
  GridLine("a composed mark", ("a" + text(LETTER_E, ACUTE) + "x").repeat(5)),
  GridLine("a mark of its own glyph", ("a" + text(LETTER_E, DOT_BELOW) + "x").repeat(5)),
  GridLine("a glyph wider than its cell", ("a" + text(GRIN) + "b").repeat(6)),
  GridLine("right-to-left text", text(ALEF, BET, ALEF, BET).repeat(5)),
)
private val RTL_LINE = LINES.last()
private val CLUSTER_LINE = LINES[3]
private val TEXT = LINES.joinToString("\n") { it.text }

/**
 * The lines of the measured soft wrap case. The cluster line stays out: there the wrap position strategy moves the
 * wrap of the measured path to the emoji, and the grid fast path keeps the wrap at the column.
 */
private val MEASURED_TEXT = LINES.filter { it !== CLUSTER_LINE }.joinToString("\n") { it.text }

/**
 * Checks that one cell count gives the layout of a grid editor: the caret x of each offset, the x of each visual
 * column, and the soft wraps of the measured path and of the grid fast path.
 *
 * Each code point of [MockFontLayoutService] has its own advance, and a cell is [CELL_WIDTH] wide. The glyphs come
 * from [GridFontLayoutService], which joins a mark and a zero width joiner sequence into one cluster.
 */
@TestApplication
internal class EditorCharacterGridLayoutTest {
  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `the caret x of each offset is the edge of its cell`(): Unit = withEditor { editor ->
    // the caret of right-to-left text moves to the left, so that text has its own case
    val lines = LINES.filter { it !== RTL_LINE }

    val x = lines.associate { line ->
      val start = editor.document.getLineStartOffset(LINES.indexOf(line))
      line.name to line.codePointOffsets().map { editor.offsetToXY(start + it).x }
    }

    assertThat(x).isEqualTo(lines.associate { it.name to it.cellEdges() })
  }

  @Test
  fun `each visual column is a code point on its cell edge`(): Unit = withEditor { editor ->
    val lines = LINES.filter { it !== RTL_LINE }

    val columns = lines.associate { line ->
      val start = editor.document.getLineStartOffset(LINES.indexOf(line))
      line.name to line.codePointOffsets().map { editor.offsetToVisualPosition(start + it).column }
    }
    val x = lines.associate { line ->
      val lineIndex = LINES.indexOf(line)
      line.name to line.codePointOffsets().indices.map { editor.visualPositionToXY(VisualPosition(lineIndex, it)).x }
    }

    assertThat(columns).isEqualTo(lines.associate { line -> line.name to line.codePointOffsets().indices.toList() })
    assertThat(x).isEqualTo(lines.associate { it.name to it.cellEdges() })
  }

  @Test
  fun `right-to-left text keeps one cell for each of its code points`(): Unit = withEditor { editor ->
    val lineIndex = LINES.indexOf(RTL_LINE)
    val columns = 0..RTL_LINE.text.length
    val x = columns.map { editor.visualPositionToXY(VisualPosition(lineIndex, it)).x }

    assertThat(x).isEqualTo(columns.map { it * CELL_WIDTH })
  }

  @Test
  fun `the soft wraps of the grid fast path follow the cells`(): Unit = withEditor(softWraps = true) { editor ->
    assertThat(editor.characterGrid?.columns).isEqualTo(WRAP_COLUMNS)
    assertThat(softWraps(editor)).isEqualTo(expectedSoftWraps(TEXT))
  }

  @Test
  fun `the measured soft wraps equal the soft wraps of the grid fast path`() {
    val fastPath = withEditor(MEASURED_TEXT, softWraps = true) { softWraps(it) }
    val measured = withEditor(MEASURED_TEXT, softWraps = true, inlay = true) { softWraps(it) }

    assertThat(measured).isEqualTo(fastPath)
    assertThat(measured).isEqualTo(expectedSoftWraps(MEASURED_TEXT))
  }

  /**
   * Returns the offset of each soft wrap. A read of the layout first, because a soft wrap can wait for it.
   */
  private fun softWraps(editor: EditorImpl): List<Int> {
    for (offset in 0..editor.document.textLength) {
      editor.offsetToXY(offset)
    }
    return editor.softWrapModel.registeredSoftWraps.map { it.start }
  }

  /**
   * Runs [test] on a grid editor with [TEXT].
   *
   * @param inlay adds an inline inlay to the first line, which takes the soft wraps off the grid fast path
   */
  @Suppress("UNCHECKED_CAST")
  private fun <T> withEditor(
    text: String = TEXT,
    softWraps: Boolean = false,
    inlay: Boolean = false,
    test: (EditorImpl) -> T,
  ): T {
    FontLayoutService.setInstance(GridFontLayoutService())
    var result: T? = null
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("")) as EditorImpl
      try {
        editor.highlighter = LexerEditorHighlighter(PlainSyntaxHighlighter(), editor.colorsScheme)
        editor.setCharacterGridSpec(CharacterGridSpec(1f, DoubleWidthCharacterStrategy { WIDE_CODE_POINTS.contains(it) }))
        ApplicationManager.getApplication().runWriteAction { editor.document.setText(text) }
        if (inlay) {
          EditorTestUtil.addInlay(editor, 1, false, 1)
        }
        if (softWraps) {
          // the soft wrap painter takes no width, so the measured path and the grid fast path have the same limit
          EditorTestUtil.configureSoftWraps(editor, WRAP_COLUMNS * CELL_WIDTH + 1, 1000, 0, false)
        }
        result = test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
    return result as T
  }
}

/**
 * One line of the test document.
 */
private class GridLine(val name: String, val text: String) {
  /**
   * Returns the offset of each code point of the line, and the offset after the last one.
   */
  fun codePointOffsets(): List<Int> {
    val offsets = mutableListOf<Int>()
    var offset = 0
    while (offset < text.length) {
      offsets.add(offset)
      offset += Character.charCount(text.codePointAt(offset))
    }
    offsets.add(text.length)
    return offsets
  }

  /**
   * Returns the x of the cell edge after each code point of the line, and the x of the line start.
   */
  fun cellEdges(): List<Int> {
    val cells = cellsBeforeOffset()
    return codePointOffsets().map { cells[it] * CELL_WIDTH }
  }

  /**
   * Returns the number of cells before each offset of the line, and before the offset after the last char.
   */
  private fun cellsBeforeOffset(): IntArray {
    val cells = IntArray(text.length + 1)
    var offset = 0
    while (offset < text.length) {
      val codePoint = text.codePointAt(offset)
      val next = offset + Character.charCount(codePoint)
      for (i in offset + 1..next) {
        cells[i] = cells[offset] + cellsOf(codePoint)
      }
      offset = next
    }
    return cells
  }
}

/**
 * Returns the number of cells of the code point, as [CharacterGridSpec.cells] does for the strategy of the test.
 */
private fun cellsOf(codePoint: Int): Int = if (WIDE_CODE_POINTS.contains(codePoint)) 2 else 1

/**
 * Returns the offset of each soft wrap of the text, as the cell count gives them.
 */
private fun expectedSoftWraps(text: String): List<Int> {
  val wraps = mutableListOf<Int>()
  var cells = 0
  var offset = 0
  while (offset < text.length) {
    val codePoint = text.codePointAt(offset)
    if (codePoint == '\n'.code) {
      cells = 0
      offset++
      continue
    }
    if (cells + cellsOf(codePoint) > WRAP_COLUMNS) {
      wraps.add(offset)
      cells = 0
    }
    cells += cellsOf(codePoint)
    offset += Character.charCount(codePoint)
  }
  return wraps
}


private fun text(vararg codePoints: Int): String = codePoints.joinToString("") { String(Character.toChars(it)) }

/**
 * Returns the advance of the code point. A joining code point takes no advance of its own.
 */
private fun advanceOf(codePoint: Int): Double {
  if (codePoint == CJK) return 13.0
  if (codePoint == GRIN || codePoint == MAN || codePoint == WOMAN) return 17.0
  if (JOINING_CODE_POINTS.contains(codePoint)) return 0.0
  return CELL_WIDTH.toDouble()
}

/**
 * Lays the text out as a font does: a mark and a zero width joiner sequence join the cluster of the base before them.
 * [DOT_BELOW] stands for a mark that the font does not compose: it keeps a glyph of its own in the cluster.
 */
private class GridFontLayoutService : MockFontLayoutService(IntToDoubleFunction { advanceOf(it) }, LINE_HEIGHT, DESCENT) {
  override fun layoutGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
    isRtl: Boolean,
  ): GlyphVector {
    val glyphs = mutableListOf<TestGlyph>()
    var offset = start
    var afterJoiner = false
    while (offset < end) {
      val codePoint = Character.codePointAt(chars, offset, end)
      val joins = afterJoiner || JOINING_CODE_POINTS.contains(codePoint)
      if (!joins) {
        glyphs.add(TestGlyph(offset - start, advanceOf(codePoint)))
      }
      else if (codePoint == DOT_BELOW) {
        glyphs.add(TestGlyph(offset - start, 0.0))
      }
      afterJoiner = codePoint == ZWJ
      offset += Character.charCount(codePoint)
    }
    return TestGlyphVector(if (isRtl) glyphs.reversed() else glyphs, isRtl)
  }

  /**
   * One glyph of [TestGlyphVector]: the char index of its cluster, and its advance.
   */
  private class TestGlyph(val charIndex: Int, val advance: Double)

  /**
   * A glyph vector whose glyphs follow each other by their advances, in visual order.
   */
  private class TestGlyphVector(
    private val glyphs: List<TestGlyph>,
    private val rtl: Boolean,
  ) : AbstractMockGlyphVector() {
    private val positions: DoubleArray = DoubleArray(glyphs.size + 1).also {
      for (i in glyphs.indices) {
        it[i + 1] = it[i] + glyphs[i].advance
      }
    }

    override fun getNumGlyphs(): Int = glyphs.size

    override fun getGlyphCharIndex(glyphIndex: Int): Int = glyphs[glyphIndex].charIndex

    override fun getGlyphPosition(glyphIndex: Int): Point2D = Point2D.Double(positions[glyphIndex], 0.0)

    override fun setGlyphPosition(glyphIndex: Int, newPos: Point2D) {
      positions[glyphIndex] = newPos.x
    }

    override fun getGlyphLogicalBounds(glyphIndex: Int): Shape {
      return Rectangle2D.Double(positions[glyphIndex], -DESCENT.toDouble(), glyphs[glyphIndex].advance, LINE_HEIGHT.toDouble())
    }

    override fun getLayoutFlags(): Int = if (rtl) FLAG_RUN_RTL else 0
  }
}
