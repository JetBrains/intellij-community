// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font

private const val FAMILY_WIDTH = 7
private const val OTHER_WIDTH = 10
private const val MISSING_FAMILY = "No Such Font Family"

/**
 * Checks that the text layout uses the fonts of the resolved font families of a text range.
 * [FamilyFontLayoutService] gives the characters of one font family their own width.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontFamiliesLayoutTest {
  private val families = FontFamilyService.getAvailableFamilies().filter { !it.startsWith(".") }
  private val family = families[0]
  private val otherFamily = families[1]

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(FamilyFontLayoutService(mapOf(family to FAMILY_WIDTH), OTHER_WIDTH))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `text with resolved font families gets the widths of the families`(): Unit = withEditor("aaaa bbbb") { editor ->
    val value = FontFamilies.of(family)
    editor.view.resolveFontFamilies(listOf(value))

    addFontFamilies(editor, 5, 9, value)

    assertThat(width(editor, 5, 9)).isEqualTo(4 * FAMILY_WIDTH)
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
  }

  @Test
  fun `the first layout of unresolved font families gets the widths of the editor fonts`(): Unit = withEditor("aaaa") { editor ->
    highlightAllTextWithoutKey(editor, FontFamilies.of(family))

    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
  }

  @Test
  fun `the resolved font families of the editor highlighter get the widths of the families`(): Unit = withEditor("aaaa") { editor ->
    editor.view.resolveFontFamilies(listOf(FontFamilies.of(family)))

    highlightAllTextWithoutKey(editor, FontFamilies.of(family))

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `two values with the same installed families make one fragment`(): Unit = withEditor("aaaa") { editor ->
    val installed = FontFamilies.of(family)
    val withMissingFamily = FontFamilies.of(MISSING_FAMILY, family)
    editor.view.resolveFontFamilies(listOf(installed, withMissingFamily))

    addFontFamilies(editor, 0, 2, installed)
    addFontFamilies(editor, 2, 4, withMissingFamily)

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
    assertThat(fragmentCount(editor)).isEqualTo(1)
  }

  @Test
  fun `a change of the fonts starts a new fragment`(): Unit = withEditor("aaaa") { editor ->
    val first = FontFamilies.of(family)
    val second = FontFamilies.of(otherFamily)
    editor.view.resolveFontFamilies(listOf(first, second))

    addFontFamilies(editor, 0, 2, first)
    addFontFamilies(editor, 2, 4, second)

    assertThat(width(editor, 0, 2)).isEqualTo(2 * FAMILY_WIDTH)
    assertThat(width(editor, 2, 4)).isEqualTo(2 * OTHER_WIDTH)
    assertThat(fragmentCount(editor)).isEqualTo(2)
  }

  @Test
  fun `the font families keep the font style of the text`(): Unit = withEditor("aaaa") { editor ->
    val value = FontFamilies.of(family)
    editor.view.resolveFontFamilies(listOf(value))
    val attributes = TextAttributes()
    attributes.fontFamilies = value
    attributes.fontType = Font.BOLD

    editor.markupModel.addRangeHighlighter(0, 4, HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE)

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
    val fonts = editor.view.fontResolver.fonts(value)
    val bold = fonts.fontAbleToDisplay("a", 0, 1, Font.BOLD, editor.view.fontResolver.fontRenderContext())
    assertThat(bold.font.isBold).isTrue()
  }

  private fun addFontFamilies(editor: EditorImpl, start: Int, end: Int, value: FontFamilies) {
    val attributes = TextAttributes()
    attributes.fontFamilies = value
    editor.markupModel.addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE)
  }

  private fun width(editor: EditorImpl, start: Int, end: Int): Int {
    return editor.offsetToXY(end).x - editor.offsetToXY(start).x
  }

  /**
   * Returns the number of fragments of the first line, after its layout.
   */
  private fun fragmentCount(editor: EditorImpl): Int {
    val view = editor.view
    val text = editor.document.documentText
    var count = 0
    view.textLayoutCache.getLineLayout(text, 0).forEachChunk { chunk ->
      chunk.ensureLayout(view, text, 0, 0)
      count += chunk.fragmentCount()
    }
    return count
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
