// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.psi.tree.IElementType

/**
 * Makes the editor highlighter of [editor] give [key] to all text, and makes the scheme of the editor give [value] to [key].
 * The editor highlighter does not resolve the font families of its attributes.
 * So the text gets them only after another source resolves them.
 */
internal fun highlightAllText(editor: EditorEx, key: TextAttributesKey, value: FontFamilies) {
  val attributes = TextAttributes()
  attributes.fontFamilies = value
  editor.colorsScheme.setAttributes(key, attributes)
  val syntaxHighlighter = object : PlainSyntaxHighlighter() {
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = arrayOf(key)
  }
  editor.highlighter = LexerEditorHighlighter(syntaxHighlighter, editor.colorsScheme)
}
