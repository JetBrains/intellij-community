// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class DelegateLexer(val delegate: Lexer) : LexerBase() {
  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    delegate.start(buffer, startOffset, endOffset, initialState)
  }

  override fun getState(): Int {
    return delegate.getState()
  }

  override fun getTokenType(): SyntaxElementType? {
    return delegate.getTokenType()
  }

  override fun getTokenStart(): Int {
    return delegate.getTokenStart()
  }

  override fun getTokenEnd(): Int {
    return delegate.getTokenEnd()
  }

  override fun advance() {
    delegate.advance()
  }

  override fun getBufferSequence(): CharSequence {
    return delegate.getBufferSequence()
  }

  override fun getBufferEnd(): Int {
    return delegate.getBufferEnd()
  }
}
