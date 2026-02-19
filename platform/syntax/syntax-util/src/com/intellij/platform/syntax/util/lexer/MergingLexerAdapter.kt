// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class MergingLexerAdapter(
  original: Lexer,
  private val tokenSet: SyntaxElementTypeSet,
) : MergingLexerAdapterBase(original) {
  override fun merge(tokenType: SyntaxElementType, lexer: Lexer): SyntaxElementType {
    if (!tokenSet.contains(tokenType)) {
      return tokenType
    }

    while (true) {
      val token = lexer.getTokenType()
      if (token !== tokenType) break
      lexer.advance()
    }
    return tokenType
  }
}