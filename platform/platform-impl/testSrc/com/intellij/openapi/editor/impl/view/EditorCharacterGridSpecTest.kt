// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
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
import java.awt.Dimension
import java.awt.Point

private const val CHAR_WIDTH = 10
private const val LINE_HEIGHT = 10
private const val DESCENT = 2
private const val WRAP_COLUMNS = 10
private const val WIDE_CHAR = '中'

private val WIDE_LINE = listOf("ab", "cd", "ef", "gh", "ij", "kl", "mn", "op").joinToString(WIDE_CHAR.toString())
private val TEXT = "x".repeat(30) + "\n" + WIDE_LINE
private val SINGLE_WIDTH = DoubleWidthCharacterStrategy { false }
private val WIDE = DoubleWidthCharacterStrategy { it == WIDE_CHAR.code }

/**
 * Checks the character grid spec of a real editor.
 * Each char of [MockFontLayoutService] has the advance [CHAR_WIDTH], so a cell is [CHAR_WIDTH] times the column
 * spacing.
 */
@TestApplication
internal class EditorCharacterGridSpecTest {
  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `the facade follows the spec and a read gives the same instance`(): Unit = withEditor(spec = null) { editor ->
    assertThat(editor.characterGrid).isNull()

    editor.setCharacterGridSpec(CharacterGridSpec(1f, SINGLE_WIDTH))

    val grid = editor.characterGrid
    assertThat(grid).isNotNull()
    assertThat(editor.characterGrid).isSameAs(grid)

    editor.setCharacterGridSpec(null)

    assertThat(editor.characterGrid).isNull()
  }

  @Test
  fun `the strategy survives a change of the multiplier`(): Unit = withEditor(CharacterGridSpec(1f, WIDE)) { editor ->
    assertThat(editor.characterGrid!!.codePointWidth(WIDE_CHAR.code)).isEqualTo(2f * CHAR_WIDTH)

    editor.setColumnSpacing(1.5f)

    val grid = checkNotNull(editor.characterGrid)
    assertThat(grid.charWidth).isEqualTo(1.5f * CHAR_WIDTH)
    assertThat(grid.codePointWidth(WIDE_CHAR.code)).isEqualTo(2f * 1.5f * CHAR_WIDTH)
  }

  @Test
  fun `a change of the multiplier moves the soft wraps`() {
    val fresh = withEditor(CharacterGridSpec(1.5f, SINGLE_WIDTH)) { capture(it) }
    withEditor(CharacterGridSpec(1f, SINGLE_WIDTH), gridFirst = false) { editor ->
      val before = capture(editor)

      editor.setColumnSpacing(1.5f)

      val after = capture(editor)
      assertThat(after.softWraps).isNotEmpty()
      assertThat(after).isNotEqualTo(before)
      assertThat(after).isEqualTo(fresh)
    }
  }

  @Test
  fun `a change of the strategy moves the layout`() {
    val fresh = withEditor(CharacterGridSpec(1f, WIDE)) { capture(it) }
    withEditor(CharacterGridSpec(1f, SINGLE_WIDTH), gridFirst = false) { editor ->
      val before = capture(editor)

      editor.setStrategy(WIDE)

      val after = capture(editor)
      assertThat(after.points).isNotEqualTo(before.points)
      assertThat(after).isEqualTo(fresh)
    }
  }

  @Test
  fun `an equal spec resets nothing`(): Unit = withEditor(CharacterGridSpec(1f, WIDE)) { editor ->
    val before = editor.view.snapshot

    editor.setCharacterGridSpec(CharacterGridSpec(1f, WIDE))

    assertThat(editor.view.snapshot).isSameAs(before)

    editor.setCharacterGridSpec(CharacterGridSpec(1.5f, WIDE))

    assertThat(editor.view.snapshot).isNotSameAs(before)
  }

  /**
   * Changes only the column spacing of the spec of the editor.
   */
  private fun EditorImpl.setColumnSpacing(columnSpacing: Float) {
    setCharacterGridSpec(checkNotNull(characterGridSpec).copy(columnSpacing = columnSpacing))
  }

  /**
   * Changes only the double-width strategy of the spec of the editor.
   */
  private fun EditorImpl.setStrategy(strategy: DoubleWidthCharacterStrategy) {
    setCharacterGridSpec(checkNotNull(characterGridSpec).copy(doubleWidthCharacterStrategy = strategy))
  }

  /**
   * The layout of one editor. A capture maps each offset first, so that dirty soft wraps recalculate.
   */
  private data class Layout(val points: List<Point>, val softWraps: List<Int>, val size: Dimension)

  private fun capture(editor: EditorImpl): Layout {
    val points = (0..editor.document.textLength).map { editor.offsetToXY(it) }
    return Layout(
      points = points,
      softWraps = editor.softWrapModel.registeredSoftWraps.map { it.start },
      size = editor.contentComponent.preferredSize,
    )
  }

  /**
   * Runs [test] on an editor with [TEXT] and soft wraps.
   * [gridFirst] sets the spec before the text, so that no layout comes from another spec.
   */
  @Suppress("UNCHECKED_CAST")
  private fun <T> withEditor(spec: CharacterGridSpec?, gridFirst: Boolean = true, test: (EditorImpl) -> T): T {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
    var result: T? = null
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("")) as EditorImpl
      try {
        editor.highlighter = LexerEditorHighlighter(PlainSyntaxHighlighter(), editor.colorsScheme)
        if (gridFirst) {
          editor.setCharacterGridSpec(spec)
        }
        ApplicationManager.getApplication().runWriteAction { editor.document.setText(TEXT) }
        if (!gridFirst) {
          editor.setCharacterGridSpec(spec)
        }
        EditorTestUtil.configureSoftWraps(editor, WRAP_COLUMNS, false)
        result = test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
    return result as T
  }
}
