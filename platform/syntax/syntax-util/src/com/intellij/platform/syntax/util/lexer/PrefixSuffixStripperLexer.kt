// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.util.text.CharArrayUtilKmp
import com.intellij.util.text.CharArrayUtilKmp.regionMatches
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PrefixSuffixStripperLexer(
  private val prefix: String,
  private val prefixType: SyntaxElementType?,
  private val suffix: String,
  private val suffixType: SyntaxElementType?,
  private val middleTokenType: SyntaxElementType?
) : LexerBase() {

  private lateinit var buffer: CharSequence
  private var bufferArray: CharArray? = null
  private var tokenStart = 0
  private var tokenEnd = 0
  private var tokenType: SyntaxElementType? = null
  private var state: Int = 0
  private var bufferEnd: Int = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    bufferArray = CharArrayUtilKmp.fromSequenceWithoutCopying(buffer)
    tokenStart = startOffset
    tokenEnd = startOffset
    tokenType = null
    state = initialState
    bufferEnd = endOffset
  }

  override fun getTokenType(): SyntaxElementType? {
    locateToken()
    return tokenType
  }

  override fun getTokenStart(): Int {
    locateToken()
    return tokenStart
  }

  override fun getTokenEnd(): Int {
    locateToken()
    return tokenEnd
  }

  override fun getState(): Int {
    return state
  }

  override fun getBufferEnd(): Int {
    return bufferEnd
  }

  override fun getBufferSequence(): CharSequence {
    return buffer
  }

  override fun advance() {
    tokenType = null
  }

  private fun locateToken() {
    if (tokenType != null) return

    when (state) {
      0 -> {
        tokenEnd = tokenStart + prefix.length
        tokenType = prefixType
        state = if (tokenEnd < bufferEnd) 1 else 3
      }

      1 -> {
        tokenStart = tokenEnd
        val suffixStart = bufferEnd - suffix.length
        tokenType = middleTokenType

        val bufferMatches = when (val bufferArray = bufferArray) {
          null -> buffer.regionMatches(suffixStart, bufferEnd, suffix)
          else -> bufferArray.regionMatches(suffixStart, bufferEnd, suffix)
        }
        if (bufferMatches) {
          tokenEnd = suffixStart
          if (tokenStart < tokenEnd) {
            state = 2
          }
          else {
            state = 3
            tokenType = suffixType
            tokenEnd = bufferEnd
          }
        }
        else {
          tokenEnd = bufferEnd
          state = 3
        }
      }

      2 -> {
        tokenStart = tokenEnd
        tokenEnd = bufferEnd
        tokenType = suffixType
        state = 3
      }

      3 -> {
        /*do nothing*/
      }

      else -> {
        throw IllegalStateException("Unexpected state: $state")
      }
    }
  }
}
