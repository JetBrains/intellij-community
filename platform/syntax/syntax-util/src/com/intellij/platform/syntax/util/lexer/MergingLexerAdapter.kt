// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.util.cancellation.cancellationProvider

/**
 * Characters of text a merge run may cover between two cancellation checks. Counting characters rather than merged
 * sub-tokens keeps the bound meaningful whatever the sub-tokens happen to be: a run of a few thousand single-character
 * tokens costs about as much as one of a few large ones, and only the text covered says how much work that was.
 */
private const val CANCELLATION_CHECK_INTERVAL = 8 * 1024

open class MergingLexerAdapter(
  original: Lexer,
  private val tokenSet: SyntaxElementTypeSet,
) : MergingLexerAdapterBase(original) {
  override fun merge(tokenType: SyntaxElementType, lexer: Lexer): SyntaxElementType {
    if (!tokenSet.contains(tokenType)) {
      return tokenType
    }

    // A merged run has no upper bound -- a megabyte of comment or character data collapses into a single token -- and it
    // is produced by one getTokenType() call, so a caller counting tokens cannot bound this loop from the outside.
    // The provider is resolved only once a run gets long, which keeps the overwhelmingly common short merges free.
    var nextCancellationCheckAt = lexer.getTokenStart() + CANCELLATION_CHECK_INTERVAL
    var cancellation: CancellationProvider? = null
    while (true) {
      if (lexer.getTokenStart() >= nextCancellationCheckAt) {
        if (cancellation == null) cancellation = cancellationProvider() ?: NoCancellation
        cancellation.checkCancelled()
        nextCancellationCheckAt = lexer.getTokenStart() + CANCELLATION_CHECK_INTERVAL
      }
      val token = lexer.getTokenType()
      if (token !== tokenType) break
      lexer.advance()
    }
    return tokenType
  }
}

private object NoCancellation : CancellationProvider {
  override fun checkCancelled() {}
}