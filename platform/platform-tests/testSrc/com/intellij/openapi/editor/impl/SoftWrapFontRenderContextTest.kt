// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.FontMetrics
import java.awt.image.BufferedImage

private const val TEXT = "ab cd ef"
private const val WRAP_COLUMN = 10
private const val SCALE = 2.0

/**
 * Checks that a change of the font render context recalculates the soft wraps.
 *
 * [ScaleWidthFontLayoutService] multiplies the character width by the scale of the font render context.
 * So [TEXT] fits in one line at [WRAP_COLUMN] without a scale, and it does not fit with [SCALE].
 */
@TestApplication
class SoftWrapFontRenderContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testScaledFontRenderContextAddsSoftWrap(): Unit = fixture.runTest {
    FontLayoutService.setInstance(ScaleWidthFontLayoutService())
    initText(TEXT)
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The text must fit in one line without a scale")

    paintWithScale()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The scaled text must not fit in one line")
    assertEquals(expected, offsets, "The soft wraps must match a full recalculation")
  }
}

/**
 * Paints the editor with a graphics that scales by [SCALE], so the editor gets a new font render context.
 */
private fun EditorModelFixture.paintWithScale() {
  val graphics = BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
  try {
    graphics.scale(SCALE, SCALE)
    graphics.clipRect(0, 0, 100, 50)
    (editor as EditorImpl).view.paint(graphics)
  }
  finally {
    graphics.dispose()
  }
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
  applianceManager.recalculateIfNecessary("SoftWrapFontRenderContextTest")
  return softWrapOffsets()
}

/**
 * Gives each character the width of [MockFontLayoutService], multiplied by the scale of the font render context.
 */
private class ScaleWidthFontLayoutService : MockFontLayoutService(
  EditorModelFixture.TEST_CHAR_WIDTH,
  EditorModelFixture.TEST_LINE_HEIGHT,
  EditorModelFixture.TEST_DESCENT,
) {
  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = width(fontMetrics)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = width(fontMetrics)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float = width(fontMetrics).toFloat()

  private fun width(fontMetrics: FontMetrics): Int {
    return (EditorModelFixture.TEST_CHAR_WIDTH * fontMetrics.fontRenderContext.transform.scaleX).toInt()
  }
}
