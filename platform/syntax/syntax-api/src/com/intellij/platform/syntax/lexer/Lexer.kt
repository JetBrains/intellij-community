// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for breaking a file into a sequence of tokens.
 *
 * @see [Implementing Lexer](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html)
 * @see RestartableLexer
 *
 * // todo bring RestartableLexer to this library
 */
@ApiStatus.Experimental
interface Lexer {
  /**
   * Prepare for lexing character data from `buffer` passed. Internal lexer state is supposed to be `initialState`. It is guaranteed
   * that the value of initialState is the same as returned by [.getState] method of this lexer at condition `startOffset=getTokenStart()`.
   * This method is used to incrementally re-lex changed characters using lexing data acquired from this particular lexer sometime in the past.
   *
   * @param buffer       character data for lexing.
   * @param startOffset  offset to start lexing from
   * @param endOffset    offset to stop lexing at
   * @param initialState the initial state of the lexer.
   */
  fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int)

  fun start(buf: CharSequence, start: Int, end: Int)

  fun start(buf: CharSequence)

  fun getTokenSequence(): CharSequence {
    return getBufferSequence().subSequence(getTokenStart(), getTokenEnd())
  }

  fun getTokenText(): String {
    return getTokenSequence().toString()
  }

  /**
   * Returns the current state of the lexer.
   *
   * @return the lexer state.
   */
  fun getState(): Int

  /**
   * Returns the token at the current position of the lexer or `null` if lexing is finished.
   *
   * @return the current token.
   */
  fun getTokenType(): SyntaxElementType?

  /**
   * Returns the start offset of the current token.
   *
   * @return the current token start offset.
   */
  fun getTokenStart(): Int

  /**
   * Returns the end offset of the current token.
   *
   * @return the current token end offset.
   */
  fun getTokenEnd(): Int

  /**
   * Advances the lexer to the next token.
   */
  fun advance()

  /**
   * Returns the current position and state of the lexer.
   *
   * @return the lexer position and state.
   */
  fun getCurrentPosition(): LexerPosition

  /**
   * Restores the lexer to the specified state and position.
   *
   * @param position the state and position to restore to.
   */
  fun restore(position: LexerPosition)

  /**
   * Returns the buffer sequence over which the lexer is running. This method should return the
   * same buffer instance which was passed to the `start()` method.
   *
   * @return the lexer buffer.
   */
  fun getBufferSequence(): CharSequence

  /**
   * Returns the offset at which the lexer will stop lexing. This method should return
   * the length of the buffer or the value passed in the `endOffset` parameter
   * to the `start()` method.
   *
   * @return the lexing end offset
   */
  fun getBufferEnd(): Int
}
