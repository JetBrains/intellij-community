// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val EDITOR_WIDTH = 10
private const val WIDE_WIDTH = 20
private const val NARROW_WIDTH = 5
private const val VISIBLE_WIDTH = 400

/**
 * 29 characters: the line fits into [VISIBLE_WIDTH] with the editor fonts and does not fit with the wide font family.
 */
private val SHORT_LINE = "word ".repeat(6).trim()

/**
 * 59 characters: the line does not fit into [VISIBLE_WIDTH] with the editor fonts and fits with the narrow font family.
 */
private val LONG_LINE = "word ".repeat(12).trim()

/**
 * Checks that the soft wraps measure text with the fonts of the resolved font families of a text range.
 * [FamilyFontLayoutService] gives the characters of the wide font family and of the narrow font family their own widths.
 * The engine skips a line that surely fits, so the tests also compare with an editor where the engine measures each line.
 */
@TestApplication
internal class EditorFontFamiliesSoftWrapTest {
  private val families = FontFamilyService.getAvailableFamilies().filter { !it.startsWith(".") }
  private val wide = FontFamilies.of(families[0])
  private val narrow = FontFamilies.of(families[1])

  @BeforeEach
  fun setUp() {
    val familyWidths = mapOf(families[0] to WIDE_WIDTH, families[1] to NARROW_WIDTH)
    FontLayoutService.setInstance(FamilyFontLayoutService(familyWidths, EDITOR_WIDTH))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `a wide font family gives a soft wrap to a line that fits with the editor fonts`() {
    val withoutFontFamilies = softWraps(SHORT_LINE, measureEachLine = true) {}
    val withFontFamilies = softWraps(SHORT_LINE, measureEachLine = true) { editor ->
      resolveForAllText(editor, wide)
    }

    assertThat(withoutFontFamilies).isEmpty()
    assertThat(withFontFamilies).isNotEmpty()
  }

  @Test
  fun `the engine does not skip a line that does not fit with a wide font family`() {
    val skipped = softWraps(SHORT_LINE, measureEachLine = false) { editor ->
      resolveForAllText(editor, wide)
    }
    val measured = softWraps(SHORT_LINE, measureEachLine = true) { editor ->
      resolveForAllText(editor, wide)
    }

    assertThat(measured).isNotEmpty()
    assertThat(skipped).isEqualTo(measured)
  }

  @Test
  fun `a narrow font family removes a soft wrap`() {
    val withoutFontFamilies = softWraps(LONG_LINE, measureEachLine = false) {}
    val withFontFamilies = softWraps(LONG_LINE, measureEachLine = false) { editor ->
      resolveForAllText(editor, narrow)
    }

    assertThat(withoutFontFamilies).isNotEmpty()
    assertThat(withFontFamilies).isEmpty()
  }

  @Test
  fun `each text range measures with the fonts of its font families`() {
    // The narrow text is 10 * 5 pixels wide, and the wide text is 24 * 20 pixels wide, so the line does not fit.
    // With only the narrow font family or only the editor fonts, the line fits.
    val text = "word ".repeat(7).trim()
    val softWraps = softWraps(text, measureEachLine = false) { editor ->
      editor.view.resolveFontFamilies(listOf(narrow, wide))
      addFontFamilies(editor, 0, 10, narrow)
      addFontFamilies(editor, 10, text.length, wide)
    }

    assertThat(softWraps).isNotEmpty()
  }

  @Test
  fun `the first soft wraps of unresolved font families measure with the editor fonts`() {
    val softWraps = softWraps(SHORT_LINE, measureEachLine = true) { editor ->
      highlightAllTextWithoutKey(editor, wide)
    }

    assertThat(softWraps).isEmpty()
  }

  @Test
  fun `the resolved font families of the editor highlighter measure with their fonts`() {
    val softWraps = softWraps(SHORT_LINE, measureEachLine = true) { editor ->
      editor.view.resolveFontFamilies(listOf(wide))
      highlightAllTextWithoutKey(editor, wide)
    }

    assertThat(softWraps).isNotEmpty()
  }

  /**
   * Resolves [value] and gives it to all text of the editor.
   */
  private fun resolveForAllText(editor: EditorImpl, value: FontFamilies) {
    editor.view.resolveFontFamilies(listOf(value))
    addFontFamilies(editor, 0, editor.document.textLength, value)
  }

  private fun addFontFamilies(editor: EditorImpl, start: Int, end: Int, value: FontFamilies) {
    val attributes = TextAttributes()
    attributes.fontFamilies = value
    editor.markupModel.addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE)
  }

  /**
   * Returns the soft wraps of [text] in an editor of [VISIBLE_WIDTH]. The document is [text] and a line break.
   * [setUp] runs before the editor uses soft wraps.
   *
   * @param measureEachLine whether the engine measures each line. An inlay turns the skip of the lines off.
   *                        The inlay is on the empty last line, so it moves no character of [text].
   */
  private fun softWraps(text: String, measureEachLine: Boolean, setUp: (EditorImpl) -> Unit): List<String> = runInEdtAndGet {
    val factory = EditorFactory.getInstance()
    val editor = factory.createEditor(factory.createDocument(text + "\n")) as EditorImpl
    try {
      setUp(editor)
      if (measureEachLine) {
        editor.inlayModel.addInlineElement(editor.document.textLength, OnePixelRenderer)
      }
      editor.settings.isUseSoftWraps = true
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, VISIBLE_WIDTH, 1000)
      editor.softWrapModel.prepareToMapping()
      editor.softWrapModel.registeredSoftWraps.map { "${it.start}:${it.indentInColumns}:${it.indentInPixels}" }
    }
    finally {
      factory.releaseEditor(editor)
    }
  }

  private object OnePixelRenderer : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 1
  }
}
