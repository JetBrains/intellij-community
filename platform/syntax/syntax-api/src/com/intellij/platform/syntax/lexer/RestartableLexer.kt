// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import org.jetbrains.annotations.ApiStatus

/**
 * A lexer that can restart from more states than the zero state.
 * A highlighting lexer restarts at a restartable state after a document change, so many restartable states make
 * the incremental highlighting fast.
 *
 * When a lexer needs more data than the state to restart, it can read the preceding tokens with the [TokenIterator]
 * of the extended [start].
 *
 * The default storage of the editor highlighter keeps only 16 bits of a state, so a state must be in the `Short` range,
 * `-32768..32767`. The highlighter truncates a state out of this range and then restarts the lexer in a wrong state.
 * A highlighter with its own data storage can have a different range.
 *
 * This is the analogue of `com.intellij.lexer.RestartableLexer`.
 */
@ApiStatus.Experimental
interface RestartableLexer : Lexer {
  /**
   * Returns the start state of the lexer.
   *
   * @return the lexer state.
   */
  fun getStartState(): Int

  /**
   * Specifies which states are restartable.
   *
   * @param state lexer state to check
   * @return `true` if the lexer can restart from [state], `false` otherwise.
   */
  fun isRestartableState(state: Int): Boolean

  /**
   * The extended form of [Lexer.start].
   * [tokenIterator] starts at the current token and goes in the reverse order.
   * Use it to get the data that the lexer needs to restart in a context-dependent state.
   *
   * @param buffer        character data for lexing
   * @param startOffset   offset to start lexing from
   * @param endOffset     offset to stop lexing at
   * @param initialState  state to start lexing with
   * @param tokenIterator iterator for getting info from preceding tokens
   */
  fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator)
}
