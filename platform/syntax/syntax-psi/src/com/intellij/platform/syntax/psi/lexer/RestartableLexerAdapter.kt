// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.psi.lexer

import com.intellij.lexer.RestartableLexer
import com.intellij.lexer.TokenIterator
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.convertNotNull
import org.jetbrains.annotations.ApiStatus

/**
 * A [LexerAdapter] for a [com.intellij.platform.syntax.lexer.RestartableLexer].
 * It implements [RestartableLexer], so a highlighter can restart the lexer from each restartable state.
 */
@ApiStatus.Experimental
open class RestartableLexerAdapter(
  private val restartableLexer: com.intellij.platform.syntax.lexer.RestartableLexer,
  elementTypeConverter: ElementTypeConverter,
) : LexerAdapter(restartableLexer, elementTypeConverter), RestartableLexer {
  override fun getStartState(): Int = restartableLexer.getStartState()

  override fun isRestartableState(state: Int): Boolean = restartableLexer.isRestartableState(state)

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator) {
    restartableLexer.start(buffer, startOffset, endOffset, initialState, TokenIteratorAdapter(tokenIterator, elementTypeConverter))
  }
}

/**
 * Creates a [RestartableLexerAdapter] when [lexer] is a [com.intellij.platform.syntax.lexer.RestartableLexer],
 * and a [LexerAdapter] otherwise.
 */
fun createLexerAdapter(
  lexer: com.intellij.platform.syntax.lexer.Lexer,
  elementTypeConverter: ElementTypeConverter,
): LexerAdapter {
  return if (lexer is com.intellij.platform.syntax.lexer.RestartableLexer) RestartableLexerAdapter(lexer, elementTypeConverter)
  else LexerAdapter(lexer, elementTypeConverter)
}

private class TokenIteratorAdapter(
  private val iterator: TokenIterator,
  private val elementTypeConverter: ElementTypeConverter,
) : com.intellij.platform.syntax.lexer.TokenIterator {
  override fun getStartOffset(index: Int): Int = iterator.getStartOffset(index)

  override fun getEndOffset(index: Int): Int = iterator.getEndOffset(index)

  override fun getType(index: Int): SyntaxElementType = elementTypeConverter.convertNotNull(iterator.getType(index))

  override fun getState(index: Int): Int = iterator.getState(index)

  override fun getTokenCount(): Int = iterator.getTokenCount()

  override fun initialTokenIndex(): Int = iterator.initialTokenIndex()
}
