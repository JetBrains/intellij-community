// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.Language
import com.intellij.lexer.LexerPosition
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.psi.ElementTypeConverters
import com.intellij.psi.tree.IElementType


open class LexerAdapter(language: Language, private val myDelegate: Lexer) : com.intellij.lexer.Lexer() {

  private val elementTypeConverter = ElementTypeConverters.forLanguage(language)

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    myDelegate.start(buffer, startOffset, endOffset, initialState)
  }

  override fun getState(): Int {
    return myDelegate.getState()
  }

  override fun getTokenType(): IElementType? {
    val originType = myDelegate.getTokenType() ?: return null
    return elementTypeConverter.convert(originType)
  }

  override fun getTokenStart(): Int {
    return myDelegate.getTokenStart()
  }

  override fun getTokenEnd(): Int {
    return myDelegate.getTokenEnd()
  }

  override fun advance() {
    myDelegate.advance()
  }

  override fun getCurrentPosition(): LexerPosition {
    val origin = myDelegate.getCurrentPosition()
    return adaptPositionForIntellij(origin)
  }

  protected fun adaptPositionForIntellij(origin: com.intellij.platform.syntax.lexer.LexerPosition): com.intellij.lexer.LexerPosition {
    return object : com.intellij.lexer.LexerPosition {
      override fun getOffset(): Int {
        return origin.offset
      }

      override fun getState(): Int {
        return origin.state
      }

    }
  }

  protected fun adaptPositionForSyntax(origin: com.intellij.lexer.LexerPosition): com.intellij.platform.syntax.lexer.LexerPosition {
    return object : com.intellij.platform.syntax.lexer.LexerPosition {
      override val offset: Int
        get() = origin.offset
      override val state: Int
        get() = origin.state
    }
  }

  override fun restore(position: LexerPosition) {
    myDelegate.restore(adaptPositionForSyntax(position))
  }

  override fun getBufferSequence(): CharSequence {
    return myDelegate.getBufferSequence()
  }

  override fun getBufferEnd(): Int {
    return myDelegate.getBufferEnd()
  }
}