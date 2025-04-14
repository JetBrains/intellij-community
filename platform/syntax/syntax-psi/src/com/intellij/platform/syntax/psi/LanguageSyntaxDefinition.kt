// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.LanguageExtension
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.runtime.SyntaxGeneratedParserRuntime
import org.jetbrains.annotations.ApiStatus


/**
 * Extension point providing syntax implementation for a given [com.intellij.lang.Language]
 *
 * @see LanguageSyntaxDefinitions
 */
@ApiStatus.OverrideOnly
interface LanguageSyntaxDefinition {
  /**
   * An instance of [Lexer] for the language with default settings.
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

  // todo add necessary methods when required
  fun getPairedBraces(): Collection<SyntaxGeneratedParserRuntime.BracePair> = emptyList()
}

/**
 * Extension providing access to [LanguageSyntaxDefinition]s
 */
class LanguageSyntaxDefinitions : LanguageExtension<LanguageSyntaxDefinition>("com.intellij.syntax.syntaxDefinition") {
  companion object {
    @JvmStatic
    val INSTANCE: LanguageSyntaxDefinitions = LanguageSyntaxDefinitions()
  }
}