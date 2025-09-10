// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.LexerPosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class MergingLexerAdapterBase(original: Lexer) : DelegateLexer(original) {
  private var myTokenType: SyntaxElementType? = null
  private var myState = 0
  private var myTokenStart = 0

  abstract fun merge(tokenType: SyntaxElementType, lexer: Lexer): SyntaxElementType

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    super.start(buffer, startOffset, endOffset, initialState)
    myTokenType = null
    myState = 0
    myTokenStart = 0
  }

  override fun getState(): Int {
    locateToken()
    return myState
  }

  override fun getTokenType(): SyntaxElementType? {
    locateToken()
    return myTokenType
  }

  override fun getTokenStart(): Int {
    locateToken()
    return myTokenStart
  }

  override fun getTokenEnd(): Int {
    locateToken()
    return super.getTokenStart()
  }

  override fun advance() {
    myTokenType = null
    myState = 0
    myTokenStart = 0
  }

  private fun locateToken() {
    if (myTokenType == null) {
      val orig = delegate

      myTokenType = orig.getTokenType()
      myTokenStart = orig.getTokenStart()
      myState = orig.getState()

      val tokenType = myTokenType ?: return
      orig.advance()
      myTokenType = merge(tokenType, orig)
    }
  }

  val original: Lexer
    get() = delegate

  override fun restore(position: LexerPosition) {
    val pos = position as MyLexerPosition

    delegate.restore(pos.originalPosition)
    myTokenType = pos.type
    myTokenStart = pos.offset
    myState = pos.oldState
  }

  override fun toString(): String {
    return "${this::class.simpleName}[$delegate]"
  }

  override fun getCurrentPosition(): LexerPosition =
    MyLexerPosition(myTokenStart, myTokenType, delegate.getCurrentPosition(), myState)
}

private class MyLexerPosition(
  override val offset: Int,
  val type: SyntaxElementType?,
  val originalPosition: LexerPosition,
  val oldState: Int,
) : LexerPosition {

  override val state: Int
    get() = originalPosition.state
}
