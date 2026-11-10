// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.CustomFoldRegionRendererEx
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

private const val FAMILY_WIDTH = 30
private const val FAMILY_HEIGHT = 40
private const val FAMILY_DESCENT = 10
private const val SHORT_FAMILY_DESCENT = 2
private const val OTHER_WIDTH = 10
private const val MISSING_FAMILY = "No Such Font Family"

/**
 * Checks that the overhangs and the width estimates of the editor cover the resolved font families.
 * [FamilyFontLayoutService] gives the fonts of one font family a larger width, height and descent than the editor fonts.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontFamiliesMetricsTest {
  private val family = FontFamilyService.getAvailableFamilies().first { !it.startsWith(".") }
  private val value = FontFamilies.of(family)

  @BeforeEach
  fun setUp() {
    val layoutService = FamilyFontLayoutService(
      familyWidths = mapOf(family to FAMILY_WIDTH),
      otherWidth = OTHER_WIDTH,
      familyHeights = mapOf(family to FAMILY_HEIGHT),
      familyDescents = mapOf(family to FAMILY_DESCENT),
    )
    FontLayoutService.setInstance(layoutService)
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `a tall resolved font family widens the overhangs`(): Unit = withEditor { editor ->
    val view = editor.view

    view.resolveFontFamilies(listOf(value))

    assertThat(view.topOverhang).isEqualTo(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a value without installed families keeps the overhangs of the editor fonts`(): Unit = withEditor { editor ->
    val view = editor.view
    val topOverhang = view.topOverhang
    val bottomOverhang = view.bottomOverhang

    view.resolveFontFamilies(listOf(FontFamilies.of(MISSING_FAMILY)))

    assertThat(view.topOverhang).isEqualTo(topOverhang).isLessThan(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(bottomOverhang).isLessThan(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a reinit keeps the overhangs of the resolved font families`(): Unit = withEditor { editor ->
    val view = editor.view
    view.resolveFontFamilies(listOf(value))

    editor.reinitSettings()

    assertThat(view.topOverhang).isEqualTo(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a wide resolved font family raises the width estimate and keeps the cells of the character grid`(): Unit = withEditor { editor ->
    val view = editor.view
    editor.settings.characterGridWidthMultiplier = 1f
    val cellWidth = editor.characterGrid!!.charWidth

    view.resolveFontFamilies(listOf(value))

    assertThat(view.maxCharWidth).isEqualTo(FAMILY_WIDTH.toFloat())
    assertThat(editor.characterGrid!!.charWidth).isEqualTo(cellWidth).isEqualTo(OTHER_WIDTH.toFloat())
  }

  @Test
  fun `a resolved tall font family that no text has reaches no line`(): Unit = withVisibleEditor("a\nb\nc") { editor ->
    val view = editor.view
    view.resolveFontFamilies(listOf(value))
    layOut(editor, 0..2)

    assertThat(view.topOverhang).isPositive()
    assertThat(reachedLines(editor, 0..2)).isEmpty()
  }

  @Test
  fun `a tall font family reaches only the lines next to its line`(): Unit = withVisibleEditor("a\nb\nc\nd\ne") { editor ->
    addFontFamilies(editor, line = 2)
    layOut(editor, 0..4)

    assertThat(reachedLines(editor, 0..4)).containsExactly(1, 3)
  }

  @Test
  fun `a font family with only a tall ascent reaches only the line above its line`() {
    val layoutService = FamilyFontLayoutService(
      familyWidths = mapOf(family to FAMILY_WIDTH),
      otherWidth = OTHER_WIDTH,
      familyHeights = mapOf(family to FAMILY_HEIGHT),
      familyDescents = mapOf(family to SHORT_FAMILY_DESCENT),
    )
    FontLayoutService.setInstance(layoutService)
    withVisibleEditor("a\nb\nc\nd\ne") { editor ->
      addFontFamilies(editor, line = 2)
      layOut(editor, 0..4)

      assertThat(editor.view.bottomOverhang).isNotPositive()
      assertThat(reachedLines(editor, 0..4)).containsExactly(1)
    }
  }

  @Test
  fun `a tall font family reaches across a thin custom fold line`(): Unit = withVisibleEditor("a\nb\nc\nd\ne") { editor ->
    addFontFamilies(editor, line = 1)
    editor.foldingModel.runBatchFoldingOperation {
      editor.foldingModel.addCustomLinesFolding(2, 2, OnePixelFoldRenderer)
    }
    layOut(editor, listOf(0, 1, 3, 4))

    // The visual line 2 is the custom fold line of 1 px. The descent of the visual line 1 reaches across it into the visual line 3.
    assertThat(reachedLines(editor, listOf(0, 1, 3, 4))).containsExactly(0, 3)
  }

  @Test
  fun `the text that a collapsed fold hides reaches no line`(): Unit = withVisibleEditor("a\nb\nc\nd\ne") { editor ->
    addFontFamilies(editor, line = 2)
    val document = editor.document
    editor.foldingModel.runBatchFoldingOperation {
      editor.foldingModel.addFoldRegion(document.getLineEndOffset(1), document.getLineStartOffset(3), "...")?.isExpanded = false
    }
    layOut(editor, 0..4)

    // The visual line 1 shows the logical lines 1 and 3. The cached layout of the hidden logical line 2 has the tall font family.
    assertThat(reachedLines(editor, 0..2)).isEmpty()
  }

  @Test
  fun `a visible line with an unknown layout reaches the lines next to it`(): Unit = withVisibleEditor("a\nb\nc\nd\ne") { editor ->
    editor.view.resolveFontFamilies(listOf(value))
    layOut(editor, 0..4)

    editor.view.textLayoutCache.invalidateLines(2, 2)

    assertThat(reachedLines(editor, 0..4)).containsExactly(1, 3)
  }

  @Test
  fun `a line outside the visible area does not count`(): Unit = withEditor("a\nb\nc\nd\ne") { editor ->
    editor.view.resolveFontFamilies(listOf(value))
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, 1000, 2 * editor.lineHeight)
    layOut(editor, 0..1)

    // The logical line 2 has no cached layout, but it is outside the visible area, so nothing of it is on the screen.
    assertThat(editor.view.isOverhungByOtherLines(1)).isFalse()
  }

  private fun addFontFamilies(editor: EditorImpl, line: Int) {
    val attributes = TextAttributes()
    attributes.fontFamilies = value
    val document = editor.document
    editor.markupModel.addRangeHighlighter(
      document.getLineStartOffset(line),
      document.getLineEndOffset(line),
      HighlighterLayer.ADDITIONAL_SYNTAX,
      attributes,
      HighlighterTargetArea.EXACT_RANGE,
    )
  }

  /**
   * Lays out each chunk of the logical lines, as a paint of these lines does.
   */
  private fun layOut(editor: EditorImpl, lines: Iterable<Int>) {
    val view = editor.view
    val text = editor.document.documentText
    for (line in lines) {
      view.textLayoutCache.getLineLayout(text, line).forEachChunk { it.ensureLayout(view, text, line, 0) }
    }
  }

  private fun reachedLines(editor: EditorImpl, visualLines: Iterable<Int>): List<Int> {
    return visualLines.filter { editor.view.isOverhungByOtherLines(it) }
  }

  /**
   * Gives a custom fold line 1 px. A renderer that is not a [CustomFoldRegionRendererEx] gets at least the line height.
   */
  private object OnePixelFoldRenderer : CustomFoldRegionRendererEx {
    override fun getMinimumHeightInPixels(): Int = 1

    override fun calcWidthInPixels(region: CustomFoldRegion): Int = 10

    override fun calcHeightInPixels(region: CustomFoldRegion): Int = 1

    override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    }
  }

  private fun withVisibleEditor(text: String, test: (EditorImpl) -> Unit) {
    withEditor(text) { editor ->
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 1000, 1000)
      test(editor)
    }
  }

  private fun withEditor(test: (EditorImpl) -> Unit) {
    withEditor("aaaa", test)
  }

  private fun withEditor(text: String, test: (EditorImpl) -> Unit) {
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument(text)) as EditorImpl
      try {
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
