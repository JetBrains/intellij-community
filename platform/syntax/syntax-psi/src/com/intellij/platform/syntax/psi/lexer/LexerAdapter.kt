// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.lexer

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.convertNotNull
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class LexerAdapter(
  val lexer: com.intellij.platform.syntax.lexer.Lexer,
  val elementTypeConverter: ElementTypeConverter,
) : Lexer() {
  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    lexer.start(buffer, startOffset, endOffset, initialState)
  }

  override fun getState(): Int = lexer.getState()

  override fun getTokenType(): IElementType? {
    val syntaxElementType = lexer.getTokenType() ?: return null
    return elementTypeConverter.convertNotNull(syntaxElementType)
  }

  override fun getTokenStart(): Int = lexer.getTokenStart()

  override fun getTokenEnd(): Int = lexer.getTokenEnd()

  override fun advance(): Unit = lexer.advance()

  override fun getCurrentPosition(): LexerPosition {
    val position = lexer.getCurrentPosition()
    return LexerPositionAdapter(position)
  }

  override fun restore(position: LexerPosition): Unit {
    lexer.restore((position as LexerPositionAdapter).position)
  }

  override fun getBufferSequence(): CharSequence = lexer.getBufferSequence()

  override fun getBufferEnd(): Int = lexer.getBufferEnd()
}

@ApiStatus.Experimental
class LexerPositionAdapter(val position: com.intellij.platform.syntax.lexer.LexerPosition) : LexerPosition {
  override fun getOffset(): Int = position.offset
  override fun getState(): Int = position.state
}