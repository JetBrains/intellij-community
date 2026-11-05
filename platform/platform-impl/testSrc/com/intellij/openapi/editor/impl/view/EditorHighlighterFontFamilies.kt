// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
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
 * No source resolves the font families of such attributes, so the text gets them only after another source resolves them.
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
