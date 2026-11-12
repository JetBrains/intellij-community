// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.testFramework.AbstractMockGlyphVector
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.ui.Graphics2DDelegate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.image.BufferedImage

private const val CELL_WIDTH = 10f
private const val LINE_HEIGHT = 10
private const val DESCENT = 2

/**
 * The cell layout of [GridTextFragment]: the cells of each char, the glyphs of each cluster, and the caret edges.
 * A case reads the glyphs of a draw, because the fragment lays them out at the first draw.
 */
class GridTextFragmentTest {
  private val image = BufferedImage(200, 20, BufferedImage.TYPE_INT_RGB)
  private val graphics: Graphics2D = image.createGraphics()

  @AfterEach
  fun tearDown() {
    graphics.dispose()
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `each ASCII char takes one cell`() {
    val layout = layOut("abc", glyph(0, 10.0), glyph(1, 10.0), glyph(2, 10.0))

    assertThat(layout.columnEdges).containsExactly(10f, 20f, 30f)
    assertThat(layout.glyphPositions).containsExactly(0.0, 10.0, 20.0, 30.0)
  }

  @Test
  fun `a cell wider than the glyph puts the glyph in the middle of the cell`() {
    val layout = layOut("abc", glyph(0, 10.0), glyph(1, 10.0), glyph(2, 10.0), cellWidth = 12f)

    assertThat(layout.columnEdges).containsExactly(12f, 24f, 36f)
    assertThat(layout.glyphPositions).containsExactly(1.0, 13.0, 25.0, 36.0)
  }

  @Test
  fun `a double-width code point takes two cells`() {
    val layout = layOut(
      "a\u4E2Db",
      glyph(0, 10.0),
      glyph(1, 13.0),
      glyph(2, 10.0),
      wide = setOf(0x4E2D),
    )

    assertThat(layout.columnEdges).containsExactly(10f, 30f, 40f)
    assertThat(layout.glyphPositions).containsExactly(0.0, 13.5, 30.0, 40.0)
  }

  @Test
  fun `a ligature glyph sits in the middle of the cells of all its chars`() {
    val layout = layOut("===", glyph(0, 12.0)) // a Harfbuzz-style ligature: the tail chars have no glyph

    assertThat(layout.columnEdges).containsExactly(10f, 20f, 30f)
    assertThat(layout.glyphPositions).containsExactly(9.0, 30.0)
  }

  @Test
  fun `a mark glyph moves with the base of its cluster`() {
    val layout = layOut(
      "a\u0301b",
      glyph(0, 10.0), // the base 'a'
      glyph(0, 0.0), // the combining acute accent, in the cluster of its base
      glyph(2, 10.0),
    )

    assertThat(layout.columnEdges).containsExactly(10f, 20f, 30f)
    assertThat(layout.glyphPositions).containsExactly(5.0, 15.0, 20.0, 30.0)
  }

  @Test
  fun `a cluster of several code points takes the cells of all of them`() {
    val layout = layOut(
      "a\uD83D\uDC68\u200D\uD83D\uDC69b",
      glyph(0, 10.0),
      glyph(1, 17.0), // one glyph for the zero width joiner sequence
      glyph(6, 10.0),
      wide = setOf(0x1F468, 0x1F469),
    )

    assertThat(layout.columnEdges).containsExactly(10f, 30f, 40f, 60f, 70f)
    assertThat(layout.glyphPositions).containsExactly(0.0, 26.5, 60.0, 70.0)
  }

  @Test
  fun `a glyph wider than its cells keeps the caret on the cell edge`() {
    val layout = layOut("ab", glyph(0, 17.0), glyph(1, 10.0))

    assertThat(layout.columnEdges).containsExactly(10f, 20f)
    assertThat(layout.glyphPositions).containsExactly(-3.5, 10.0, 20.0)
  }

  @Test
  fun `RTL text keeps the cells of its chars in logical order`() {
    val layout = layOut(
      "\u05D0\u05D1\u05D2",
      glyph(2, 10.0), // the glyphs come in visual order, so the last char is the first glyph
      glyph(1, 10.0),
      glyph(0, 13.0),
      rtl = true,
      wide = setOf(0x05D0),
    )

    assertThat(layout.columnEdges).containsExactly(10f, 20f, 40f)
    assertThat(layout.glyphPositions).containsExactly(0.0, 10.0, 23.5, 40.0)
  }

  @Test
  fun `the layout of the glyphs waits for the first draw`() {
    val layout = layOut("abc", glyph(0, 10.0), glyph(1, 10.0), glyph(2, 10.0))

    assertThat(layout.columnEdges).containsExactly(10f, 20f, 30f)
    assertThat(layout.layoutCount).isEqualTo(0)

    layout.draw()

    assertThat(layout.layoutCount).isEqualTo(1)
  }

  @Test
  fun `a repeated draw of the same range keeps the glyphs`() {
    val layout = layOut("abc", glyph(0, 10.0), glyph(1, 10.0), glyph(2, 10.0))

    val first = layout.draw()
    val second = layout.draw()

    assertThat(second.glyphs).isSameAs(first.glyphs)
    assertThat(layout.layoutCount).isEqualTo(1)

    val other = layout.draw(startOffset = 1)

    assertThat(other.glyphs).isNotSameAs(first.glyphs)
    assertThat(layout.layoutCount).isEqualTo(2)
  }

  @Test
  fun `a draw in another render context lays the glyphs out again`() {
    val layout = layOut("abc", glyph(0, 10.0), glyph(1, 10.0), glyph(2, 10.0))

    val first = layout.draw()
    val second = layout.draw(context = FontRenderContext(AffineTransform.getScaleInstance(2.0, 2.0), true, true))

    assertThat(second.glyphs).isNotSameAs(first.glyphs)
    assertThat(layout.layoutCount).isEqualTo(2)
  }

  @Test
  fun `a draw of a part of the fragment keeps the cells of that part`() {
    val layout = layOut(
      "a\u4E2Db",
      glyph(0, 10.0),
      glyph(1, 13.0),
      glyph(2, 10.0),
      wide = setOf(0x4E2D),
    )

    val part = layout.draw(startOffset = 1, x = 100f)

    assertThat(layout.lastRange).isEqualTo(1 until 3)
    assertThat(part.x).isEqualTo(100f - 10f) // the glyphs keep the x of the whole fragment
    assertThat(part.positions).containsExactly(13.5, 30.0, 40.0)
  }

  private fun layOut(
    text: String,
    vararg glyphs: TestGlyph,
    rtl: Boolean = false,
    cellWidth: Float = CELL_WIDTH,
    wide: Set<Int> = emptySet(),
  ): Layout {
    val service = TestFontLayoutService(glyphs.asList())
    FontLayoutService.setInstance(service)
    val font = Font(Font.MONOSPACED, Font.PLAIN, 1)
    val fontInfo = FontInfo(font, 1f, false, 0f, setOf(), FontRenderContext(null, false, false))
    val gridSpec = CharacterGridSpec(1f, DoubleWidthCharacterStrategy { wide.contains(it) })
    val fragment = GridTextFragment(text.toCharArray(), 0, text.length, rtl, fontInfo, gridSpec, cellWidth)
    return Layout(fragment, graphics, service)
  }
}

private fun glyph(charIndex: Int, advance: Double): TestGlyph = TestGlyph(charIndex, advance)

/**
 * The layout of one fragment: the x of each visual column edge, and the glyphs of a draw.
 */
private class Layout(
  private val fragment: GridTextFragment,
  private val graphics: Graphics2D,
  private val service: TestFontLayoutService,
) {
  /**
   * The x of the right edge of each visual column. A column of a grid fragment is a code point.
   */
  val columnEdges: List<Float>
    get() = (1..fragment.getVisualColumnCount(0f)).map { fragment.visualColumnToX(0f, it) }

  /**
   * The x of each glyph of a draw of the whole fragment, and the x after the last one.
   */
  val glyphPositions: List<Double>
    get() = draw().positions

  /**
   * The number of text layouts that the fragment asked for.
   */
  val layoutCount: Int
    get() = service.ranges.size

  /**
   * The chars of the last text layout.
   */
  val lastRange: IntRange
    get() = service.ranges.last()

  fun draw(
    startOffset: Int = 0,
    endOffset: Int = fragment.length,
    x: Float = 0f,
    context: FontRenderContext? = null,
  ): DrawnGlyphs {
    val recorder = DrawRecorder(graphics, context)
    fragment.draw(x, 0f, startOffset, endOffset).accept(recorder)
    return DrawnGlyphs(checkNotNull(recorder.glyphs), recorder.x)
  }
}

/**
 * The glyph vector of one draw, and the x of the draw.
 */
private class DrawnGlyphs(val glyphs: GlyphVector, val x: Float) {
  /**
   * The x of each glyph, and the x after the last one.
   */
  val positions: List<Double>
    get() = (0..glyphs.numGlyphs).map { glyphs.getGlyphPosition(it).x }
}

/**
 * Keeps the glyph vector of the draw. It rejects a clip, because the grid fragment draws its range without one.
 */
private class DrawRecorder(
  graphics: Graphics2D,
  private val context: FontRenderContext?,
) : Graphics2DDelegate(graphics) {
  var glyphs: GlyphVector? = null
  var x: Float = Float.NaN

  override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
    glyphs = g
    this.x = x
  }

  override fun getFontRenderContext(): FontRenderContext = context ?: super.getFontRenderContext()

  override fun clip(s: Shape?) {
    throw AssertionError("the grid fragment draws without a clip")
  }

  override fun setClip(s: Shape?) {
    throw AssertionError("the grid fragment draws without a clip")
  }
}

/**
 * Lays the glyphs of each range out as [TestGlyphVector], and keeps the range of each layout.
 */
private class TestFontLayoutService(
  private val glyphs: List<TestGlyph>,
) : MockFontLayoutService(CELL_WIDTH.toInt(), LINE_HEIGHT, DESCENT) {
  val ranges: MutableList<IntRange> = mutableListOf()

  override fun layoutGlyphVector(
    font: Font,
    fontRenderContext: FontRenderContext,
    chars: CharArray,
    start: Int,
    end: Int,
    isRtl: Boolean,
  ): GlyphVector {
    ranges.add(start until end)
    val ofRange = glyphs.filter { it.charIndex in start until end }.map { TestGlyph(it.charIndex - start, it.advance) }
    return TestGlyphVector(ofRange)
  }
}

/**
 * One glyph of [TestGlyphVector]: the char index of its cluster, and its advance.
 */
private class TestGlyph(val charIndex: Int, val advance: Double)

/**
 * A glyph vector of a fixed layout. The glyphs follow each other by their advances, in visual order.
 * Several glyphs of one char index are one cluster, as the text layout reports a mark with the start of its cluster.
 */
private class TestGlyphVector(
  private val glyphs: List<TestGlyph>,
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
}
