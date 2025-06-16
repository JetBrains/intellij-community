// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class FlexAdapter(
  val flex: FlexLexer
) : LexerBase() {
  private lateinit var myText: CharSequence
  private var myTokenType: SyntaxElementType? = null

  private var myTokenStart = 0
  private var myTokenEnd = 0

  private var myBufferEnd = 0
  private var myState = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    myText = buffer
    myTokenEnd = startOffset
    myTokenStart = myTokenEnd
    myBufferEnd = endOffset
    flex.reset(myText, startOffset, endOffset, initialState)
    myTokenType = null
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

  override fun getBufferSequence(): CharSequence = myText

  override fun getBufferEnd(): Int = myBufferEnd

  protected fun locateToken() {
    if (myTokenType != null) return
    myTokenStart = myTokenEnd
    myState = flex.yystate()
    myTokenType = flex.advance()
    myTokenEnd = flex.getTokenEnd()
  }

  override fun toString(): String = "FlexAdapter for $flex"
}
