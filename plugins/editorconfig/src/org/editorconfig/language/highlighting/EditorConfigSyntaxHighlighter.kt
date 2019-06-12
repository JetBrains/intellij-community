// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.editorconfig.configmanagement.lexer.EditorConfigLexerFactory
import org.editorconfig.language.psi.EditorConfigElementTypes

object EditorConfigSyntaxHighlighter : SyntaxHighlighterBase() {
  const val VALID_ESCAPES = " \r\n\t\\#;!?*[]{}"

  val SEPARATOR = createTextAttributesKey("EDITORCONFIG_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
  val BRACE = createTextAttributesKey("EDITORCONFIG_BRACE", DefaultLanguageHighlighterColors.BRACES)
  val BRACKET = createTextAttributesKey("EDITORCONFIG_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
  val COMMA = createTextAttributesKey("EDITORCONFIG_COMMA", DefaultLanguageHighlighterColors.COMMA)
  val IDENTIFIER = createTextAttributesKey("EDITORCONFIG_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
  val COMMENT = createTextAttributesKey("EDITORCONFIG_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
  private val BAD_CHARACTER = createTextAttributesKey("EDITORCONFIG_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

  // Added by annotators
  val VALID_CHAR_ESCAPE = createTextAttributesKey("EDITORCONFIG_VALID_CHAR_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
  val INVALID_CHAR_ESCAPE = createTextAttributesKey("EDITORCONFIG_INVALID_CHAR_ESCAPE",
                                                    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
  val PROPERTY_KEY = createTextAttributesKey("EDITORCONFIG_PROPERTY_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
  val PROPERTY_VALUE = createTextAttributesKey("EDITORCONFIG_PROPERTY_VALUE", DefaultLanguageHighlighterColors.STRING)
  val KEY_DESCRIPTION = createTextAttributesKey("EDITORCONFIG_VARIABLE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
  val PATTERN = createTextAttributesKey("EDITORCONFIG_PATTERN", DefaultLanguageHighlighterColors.KEYWORD)
  val SPECIAL_SYMBOL = createTextAttributesKey("EDITORCONFIG_SPECIAL_SYMBOL", DefaultLanguageHighlighterColors.OPERATION_SIGN)

  private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
  private val SEPARATOR_KEYS = arrayOf(SEPARATOR)
  private val BRACE_KEYS = arrayOf(BRACE)
  private val BRACKET_KEYS = arrayOf(BRACKET)
  private val COMMA_KEYS = arrayOf(COMMA)
  private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
  private val COMMENT_KEYS = arrayOf(COMMENT)

  override fun getHighlightingLexer() = EditorConfigLexerFactory.getAdapter()

  override fun getTokenHighlights(tokenType: IElementType) = when (tokenType) {
    EditorConfigElementTypes.SEPARATOR,
    EditorConfigElementTypes.COLON -> SEPARATOR_KEYS
    EditorConfigElementTypes.L_BRACKET,
    EditorConfigElementTypes.R_BRACKET -> BRACKET_KEYS
    EditorConfigElementTypes.L_CURLY,
    EditorConfigElementTypes.R_CURLY -> BRACE_KEYS
    EditorConfigElementTypes.COMMA -> COMMA_KEYS
    EditorConfigElementTypes.IDENTIFIER -> IDENTIFIER_KEYS
    EditorConfigElementTypes.LINE_COMMENT -> COMMENT_KEYS
    TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
    else -> TextAttributesKey.EMPTY_ARRAY
  }
}
