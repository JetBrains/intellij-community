// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.LexerPosition
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmOverloads

@ApiStatus.Experimental
class FilterLexer @JvmOverloads constructor(
  original: Lexer,
  private val filter: Filter?,
  private val stateFilter: BooleanArray? = null
) : DelegateLexer(original) {
  var prevTokenEnd: Int = 0
    private set

  interface Filter {
    fun reject(type: SyntaxElementType): Boolean
  }

  class SetFilter(private val set: SyntaxElementTypeSet) : Filter {
    override fun reject(type: SyntaxElementType): Boolean = type in set
  }

  val original: Lexer
    get() = delegate

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    super.start(buffer, startOffset, endOffset, initialState)
    prevTokenEnd = -1
    locateToken()
  }


  override fun advance() {
    prevTokenEnd = delegate.getTokenEnd()
    super.advance()
    locateToken()
  }

  override fun getCurrentPosition(): LexerPosition {
    return delegate.getCurrentPosition()
  }

  override fun restore(position: LexerPosition) {
    delegate.restore(position)
    this.prevTokenEnd = -1
  }

  fun locateToken() {
    while (true) {
      val delegate = delegate
      val tokenType = delegate.getTokenType() ?: break
      if (filter == null || !filter.reject(tokenType)) {
        if (stateFilter == null || !stateFilter[delegate.getState()]) {
          break
        }
      }
      delegate.advance()
    }
  }
}

