// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.psi.tree.IElementType

/**
 * Makes the editor highlighter of [editor] give [key] to all text. The scheme of the editor gives the attributes of [key].
 */
internal fun highlightAllText(editor: EditorEx, key: TextAttributesKey) {
  val syntaxHighlighter = object : PlainSyntaxHighlighter() {
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = arrayOf(key)
  }
  editor.highlighter = LexerEditorHighlighter(syntaxHighlighter, editor.colorsScheme)
}

/**
 * Makes the editor highlighter of [editor] give all text attributes with the font families [value], without a key.
 * No source resolves the font families of such attributes in advance.
 * So the first layout uses the editor fonts, and the editor resolves the value later, see `EditorImpl.recordUnresolvedFontFamilies`.
 */
internal fun highlightAllTextWithoutKey(editor: EditorEx, value: FontFamilies) {
  val attributes = TextAttributes()
  attributes.fontFamilies = value
  editor.highlighter = object : EmptyEditorHighlighter(attributes) {
    override fun setColorScheme(scheme: EditorColorsScheme) {
      // The highlighter keeps its own attributes. The editor sets its scheme, and the base class would take the attributes of a key.
    }
  }
}

/**
 * Gives all text the attributes with the font families [value], without a key, and declares the values in [declared],
 * see [com.intellij.openapi.editor.highlighter.EditorHighlighter.getTextAttributesFontFamilies].
 * It keeps its own attributes when the editor sets its scheme.
 */
internal class DeclaringHighlighter(value: FontFamilies) : EmptyEditorHighlighter(attributesWith(value)) {
  private var declared: Set<FontFamilies> = setOf()
  private var client: HighlighterClient? = null

  override fun setColorScheme(scheme: EditorColorsScheme) {
  }

  override fun setEditor(editor: HighlighterClient) {
    super.setEditor(editor)
    client = editor
  }

  override fun getTextAttributesFontFamilies(): Set<FontFamilies> {
    return declared
  }

  /**
   * Declares [value], and repaints the text from 0 to [end] as a highlighter does after a change of its attributes.
   * Before the editor gets this highlighter, it only declares the value.
   */
  fun declare(value: FontFamilies, end: Int) {
    declared = declared + value
    client?.repaint(0, end)
  }
}

private fun attributesWith(value: FontFamilies): TextAttributes {
  val attributes = TextAttributes()
  attributes.fontFamilies = value
  return attributes
}
