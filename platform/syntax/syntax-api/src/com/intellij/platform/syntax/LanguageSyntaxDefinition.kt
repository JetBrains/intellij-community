// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point providing syntax implementation for a given [com.intellij.lang.Language]
 *
 * @see com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface LanguageSyntaxDefinition {
  /**
   * An instance of [com.intellij.platform.syntax.lexer.Lexer] for the language with default settings.
   * Create your lexer directly if you need to set some custom settings.
   */
  fun getLexer(): Lexer

  /**
   * The set of whitespace token types of the language
   */
  fun getWhitespaceTokens(): SyntaxElementTypeSet = syntaxElementTypeSetOf(SyntaxTokenTypes.WHITE_SPACE)

  /**
   * The set of comment token types of the language
   */
  fun getCommentTokens(): SyntaxElementTypeSet
}