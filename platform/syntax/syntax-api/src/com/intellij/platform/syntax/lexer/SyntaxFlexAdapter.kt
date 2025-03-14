// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.NoopLogger
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

//A copy from com.intellij.lexer.FlexAdapter. Similarly to the FlexLexer class, it is necessary to return SyntaxElementType instead of IElementType
@ApiStatus.Experimental
open class SyntaxFlexAdapter(val flex: FlexLexer) : Lexer {
  private var myTokenType: SyntaxElementType? = null
  private var myText: CharSequence? = null

  private var myTokenStart = 0
  private var myTokenEnd = 0

  private var myBufferEnd = 0
  private var myState = 0

  private var myFailed = false

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    myText = buffer
    myTokenEnd = startOffset
    myTokenStart = myTokenEnd
    myBufferEnd = endOffset
    flex.reset(myText, startOffset, endOffset, initialState)
    myTokenType = null
  }

  override fun start(buf: CharSequence, start: Int, end: Int) {
    start(buf, start, end, 0)
  }

  override fun start(buf: CharSequence) {
    start(buf, 0, buf.length)
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
    return myTokenEnd
  }

  override fun advance() {
    locateToken()
    myTokenType = null
  }

  override fun getCurrentPosition(): LexerPosition {
    val offset = getTokenStart()
    val intState = getState()
    return LexerPositionImpl(offset, intState)
  }

  override fun restore(position: LexerPosition) {
    start(getBufferSequence(), position.offset, getBufferEnd(), position.state);
  }

  override fun getBufferSequence(): CharSequence {
    return myText!!
  }

  override fun getBufferEnd(): Int {
    return myBufferEnd
  }

  protected fun locateToken() {
    if (myTokenType != null) return

    myTokenStart = myTokenEnd
    if (myFailed) return

    try {
      myState = flex.yystate()
      myTokenType = flex.advance()
      myTokenEnd = flex.getTokenEnd()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      myFailed = true
      myTokenType = SyntaxTokenTypes.BAD_CHARACTER
      myTokenEnd = myBufferEnd
      LOG.warn(flex.javaClass.getName(), e)
    }
  }

  override fun toString(): String {
    return "FlexAdapter for " + flex.javaClass.getName()
  }

  companion object {
    private val LOG = NoopLogger
  }
  
  private class LexerPositionImpl(override val offset: Int, override val state: Int) : LexerPosition
}