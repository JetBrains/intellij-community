// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.Point

private const val CHAR_WIDTH = 10
private const val LINE_HEIGHT = 10
private const val DESCENT = 2
private const val FONT_SIZE = 10f
private const val LETTER_SPACING = 0.2f
private const val SPACED_CHAR_WIDTH = 12
private const val SOFT_WRAP_WIDTH = 10

/**
 * Checks the letter spacing in a real editor.
 * Each char of [MockFontLayoutService] has the advance [CHAR_WIDTH].
 * [LETTER_SPACING] at [FONT_SIZE] adds 2 pixels, so a spaced char is [SPACED_CHAR_WIDTH] wide.
 */
@TestApplication
internal class EditorLetterSpacingTest {
  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `each column moves by the letter spacing`(): Unit = withEditor("abcdef", LETTER_SPACING) { editor ->
    assertThat(editor.view.plainSpaceWidth).isEqualTo(SPACED_CHAR_WIDTH.toFloat())
    assertThat(xOfOffsets(editor, 0..6)).containsExactly(0, 12, 24, 36, 48, 60, 72)
  }

  @Test
  fun `a click goes to the nearest column boundary`(): Unit = withEditor("abcdef", LETTER_SPACING) { editor ->
    assertThat(editor.xyToLogicalPosition(Point(17, 0))).isEqualTo(LogicalPosition(0, 1))
    assertThat(editor.xyToLogicalPosition(Point(19, 0))).isEqualTo(LogicalPosition(0, 2))
  }

  @Test
  fun `the virtual space takes the letter spacing`(): Unit = withEditor("ab", LETTER_SPACING) { editor ->
    assertThat(editor.logicalPositionToXY(LogicalPosition(0, 6)).x).isEqualTo(72)
  }

  @Test
  fun `a tab stop is a number of spaced columns`() {
    withEditor("a\tb", LETTER_SPACING, tabSize = 4) { editor ->
      assertThat(xOfOffsets(editor, 0..3)).containsExactly(0, 12, 48, 60)
    }
  }

  @Test
  fun `a mark takes no letter spacing`(): Unit = withEditor("aéx", LETTER_SPACING) { editor ->
    assertThat(editor.offsetToXY(1).x).isEqualTo(12)
    assertThat(editor.offsetToXY(4).x).isEqualTo(12 + 12 + 10 + 12)
  }

  @Test
  fun `the letters of a right-to-left script take the letter spacing`(): Unit = withEditor("שלום", LETTER_SPACING) { editor ->
    assertThat(xOfVisualColumns(editor, 0..4)).containsExactly(0, 12, 24, 36, 48)
  }

  @Test
  fun `the letters of a joining script take no letter spacing`(): Unit = withEditor("سلام", LETTER_SPACING) { editor ->
    assertThat(xOfVisualColumns(editor, 0..4)).containsExactly(0, 10, 20, 30, 40)
  }

  @Test
  fun `a grid editor ignores the letter spacing`(): Unit = withEditor("abc", LETTER_SPACING) { editor ->
    editor.settings.characterGridWidthMultiplier = 1f
    editor.reinitSettings()

    assertThat(editor.view.hasLetterSpacing()).isFalse()
    assertThat(editor.view.plainSpaceWidth).isEqualTo(CHAR_WIDTH.toFloat())
    assertThat(xOfOffsets(editor, 0..3)).containsExactly(0, 10, 20, 30)
  }

  @Test
  fun `soft wraps, folds and inlays lay out as with a font of wider chars`() {
    val spaced = layout(CHAR_WIDTH, LETTER_SPACING)
    val wide = layout(SPACED_CHAR_WIDTH, 0f)
    val plain = layout(CHAR_WIDTH, 0f)

    assertThat(spaced.softWraps).isNotEmpty()
    assertThat(spaced).isEqualTo(wide).isNotEqualTo(plain)
  }

  private fun layout(charWidth: Int, letterSpacing: Float): Layout {
    val text = "fun main() {\n\tval words = listOf(\"one\", \"two\", \"three\", \"four\", \"five\")\n\tprintln(words)\n}"
    var result: Layout? = null
    withEditor(text, letterSpacing, charWidth = charWidth, tabSize = 4) { editor ->
      val foldStart = text.indexOf("\"two\"")
      EditorTestUtil.addFoldRegion(editor, foldStart, foldStart + "\"two\"".length, "...", true)
      EditorTestUtil.addInlay(editor, text.indexOf("words)"), false, 7)
      EditorTestUtil.configureSoftWraps(editor, 241, SOFT_WRAP_WIDTH)
      result = Layout(
        softWraps = editor.softWrapModel.registeredSoftWraps.map { it.start },
        points = (0..text.length).map { editor.offsetToXY(it) },
      )
    }
    return result!!
  }

  private data class Layout(val softWraps: List<Int>, val points: List<Point>)

  private fun xOfOffsets(editor: EditorImpl, offsets: IntRange): List<Int> {
    return offsets.map { editor.offsetToXY(it).x }
  }

  private fun xOfVisualColumns(editor: EditorImpl, columns: IntRange): List<Int> {
    return columns.map { editor.visualPositionToXY(VisualPosition(0, it)).x }
  }

  private fun withEditor(
    text: String,
    letterSpacing: Float,
    charWidth: Int = CHAR_WIDTH,
    tabSize: Int? = null,
    test: (EditorImpl) -> Unit,
  ) {
    FontLayoutService.setInstance(MockFontLayoutService(charWidth, LINE_HEIGHT, DESCENT))
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("")) as EditorImpl
      try {
        editor.highlighter = LexerEditorHighlighter(PlainSyntaxHighlighter(), editor.colorsScheme)
        ApplicationManager.getApplication().runWriteAction { editor.document.setText(text) }
        if (tabSize != null) {
          editor.settings.setTabSize(tabSize)
        }
        setFont(editor, letterSpacing)
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }

  private fun setFont(editor: EditorImpl, letterSpacing: Float) {
    val scheme = editor.colorsScheme
    val preferences = FontPreferencesImpl()
    scheme.fontPreferences.copyTo(preferences)
    val family = preferences.fontFamily
    preferences.clearFonts()
    preferences.register(family, FONT_SIZE)
    preferences.setUseLigatures(false)
    preferences.letterSpacing = letterSpacing
    scheme.fontPreferences = preferences
  }
}
