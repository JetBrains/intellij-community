// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.FontMetrics

private const val TEXT = "ab cd ef"
private const val WRAP_COLUMN = 10
private const val BOLD_CHAR_WIDTH = 2 * EditorModelFixture.TEST_CHAR_WIDTH
private const val STALE_SOFT_WRAPS = "The soft wraps must match a full recalculation"

/**
 * Checks that a font style change of a highlighter recalculates the soft wraps of its range and updates the editor height.
 *
 * [BoldWidthFontLayoutService] makes a bold character two times as wide as a plain character.
 * So [TEXT] fits in one line at [WRAP_COLUMN] in the plain style, and it does not fit in the bold style.
 */
@TestApplication
class SoftWrapHighlighterFontStyleTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testAddedBoldHighlighterAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    addHighlighter(boldAttributes())

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testRemovedBoldHighlighterRemovesSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    val highlighter = addHighlighter(boldAttributes())
    configureSoftWraps(WRAP_COLUMN)
    assertFalse(softWrapOffsets().isEmpty(), "The bold text must not fit in one line")

    highlighter.dispose()

    val offsets = softWrapOffsets()
    assertEquals(recalculatedSoftWrapOffsets(), offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldAttributesChangeAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    val highlighter = addHighlighter(TextAttributes())
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    (highlighter as RangeHighlighterEx).setTextAttributes(boldAttributes())

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testAddedSoftWrapUpdatesEditorHeight(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    val contentComponent = editor.contentComponent
    val oneLineHeight = contentComponent.height
    assertEquals(contentComponent.preferredSize.height, oneLineHeight, "The editor height must match its preferred height")

    addHighlighter(boldAttributes())

    assertTrue(contentComponent.preferredSize.height > oneLineHeight, "The new soft wrap must add a visual line")
    assertEquals(contentComponent.preferredSize.height, contentComponent.height, "The editor height must follow the soft wraps")
  }
}

/**
 * Sets [BoldWidthFontLayoutService] and opens a plain text file with [TEXT].
 * The fixture resets the font layout service after the test.
 */
private fun EditorModelFixture.initBoldWidthText() {
  FontLayoutService.setInstance(BoldWidthFontLayoutService())
  initText(TEXT)
}

private fun EditorModelFixture.addHighlighter(attributes: TextAttributes): RangeHighlighter {
  return editor.markupModel.addRangeHighlighter(
    /* startOffset = */ 0,
    /* endOffset = */ editor.document.textLength,
    /* layer = */ HighlighterLayer.SYNTAX,
    /* textAttributes = */ attributes,
    /* targetArea = */ HighlighterTargetArea.EXACT_RANGE,
  )
}

private fun EditorModelFixture.softWrapOffsets(): List<Int> {
  return editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength).map { it.start }
}

/**
 * Recalculates all soft wraps of the editor and returns their offsets.
 */
private fun EditorModelFixture.recalculatedSoftWrapOffsets(): List<Int> {
  val applianceManager = (editor.softWrapModel as SoftWrapModelImpl).applianceManager
  applianceManager.reset()
  applianceManager.recalculateIfNecessary("SoftWrapHighlighterFontStyleTest")
  return softWrapOffsets()
}

private fun boldAttributes(): TextAttributes {
  return TextAttributes(null, null, null, null, Font.BOLD)
}

/**
 * Gives a bold character the width [BOLD_CHAR_WIDTH].
 * Every other character gets the width of [MockFontLayoutService].
 */
private class BoldWidthFontLayoutService : MockFontLayoutService(
  EditorModelFixture.TEST_CHAR_WIDTH,
  EditorModelFixture.TEST_LINE_HEIGHT,
  EditorModelFixture.TEST_DESCENT,
) {
  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = width(fontMetrics)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = width(fontMetrics)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float = width(fontMetrics).toFloat()

  private fun width(fontMetrics: FontMetrics): Int {
    return if (fontMetrics.font.isBold) {
      BOLD_CHAR_WIDTH
    } else {
      EditorModelFixture.TEST_CHAR_WIDTH
    }
  }
}
