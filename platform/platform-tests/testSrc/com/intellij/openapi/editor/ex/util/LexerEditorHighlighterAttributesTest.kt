// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font

/**
 * Checks the text attributes that [LexerEditorHighlighter] gives to a token.
 */
@TestApplication
internal class LexerEditorHighlighterAttributesTest {
  @Test
  fun `a token type keeps one attributes instance`() {
    val highlighter = highlighter(scheme(Color.RED))
    assertThat(attributes(highlighter)).isSameAs(attributes(highlighter))
  }

  @Test
  fun `a new color scheme gives the attributes of that scheme`() {
    val highlighter = highlighter(scheme(Color.RED))
    assertThat(attributes(highlighter).foregroundColor).isEqualTo(Color.RED)
    val blue = scheme(Color.BLUE)
    highlighter.setColorScheme(blue)
    assertThat(highlighter.scheme).isSameAs(blue)
    assertThat(attributes(highlighter).foregroundColor).isEqualTo(Color.BLUE)
  }

  @Test
  fun `a storage that does not know the token index gives the attributes of the token type`() {
    val highlighter = object : LexerEditorHighlighter(OneKeySyntaxHighlighter(), scheme(Color.RED)) {
      override fun createStorage(): DataStorage {
        return object : ShortBasedStorage() {}
      }
    }
    highlighter.setText("text")
    assertThat(attributes(highlighter).foregroundColor).isEqualTo(Color.RED)
    assertThat(attributes(highlighter)).isSameAs(attributes(highlighter))
  }

  private fun scheme(foreground: Color): EditorColorsScheme {
    val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
    scheme.setAttributes(KEY, TextAttributes(foreground, null, null, null, Font.PLAIN))
    return scheme
  }

  private fun highlighter(scheme: EditorColorsScheme): LexerEditorHighlighter {
    val highlighter = LexerEditorHighlighter(OneKeySyntaxHighlighter(), scheme)
    highlighter.setText("text")
    return highlighter
  }

  private fun attributes(highlighter: LexerEditorHighlighter): TextAttributes {
    return highlighter.createIterator(0).textAttributes
  }

  private class OneKeySyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer {
      return EmptyLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
      return arrayOf(KEY)
    }
  }

  private companion object {
    val KEY: TextAttributesKey = TextAttributesKey.createTextAttributesKey("LEXER_EDITOR_HIGHLIGHTER_ATTRIBUTES_TEST")
  }
}
