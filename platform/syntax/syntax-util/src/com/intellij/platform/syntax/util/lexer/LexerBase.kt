// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.LexerPosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class LexerBase : Lexer {
  override fun getCurrentPosition(): LexerPosition {
    val offset = getTokenStart()
    val intState = getState()
    return LexerPositionImpl(offset, intState)
  }

  override fun restore(position: LexerPosition) {
    start(getBufferSequence(), position.offset, getBufferEnd(), position.state)
  }

  override fun start(buf: CharSequence, start: Int, end: Int) {
    start(buf, start, end, 0)
  }

  override fun start(buf: CharSequence) {
    start(buf, 0, buf.length)
  }
}

private class LexerPositionImpl(
  override val offset: Int,
  override val state: Int,
) : LexerPosition

