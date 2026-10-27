// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Gives the data of the preceding tokens to [RestartableLexer.start].
 *
 * This is the analogue of `com.intellij.lexer.TokenIterator`.
 */
@ApiStatus.Experimental
interface TokenIterator {
  /**
   * Returns the start offset of the token at [index].
   */
  fun getStartOffset(index: Int): Int

  /**
   * Returns the end offset of the token at [index].
   */
  fun getEndOffset(index: Int): Int

  /**
   * Returns the type of the token at [index].
   */
  fun getType(index: Int): SyntaxElementType

  /**
   * Returns the lexer state of the token at [index].
   */
  fun getState(index: Int): Int

  /**
   * Returns the number of tokens in the document.
   */
  fun getTokenCount(): Int

  /**
   * Returns the index of the token that the lexer restarts at.
   */
  fun initialTokenIndex(): Int
}
