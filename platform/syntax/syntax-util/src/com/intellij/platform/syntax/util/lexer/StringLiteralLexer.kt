// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.logger.noopLogger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class StringLiteralLexer(
  protected val quoteChar: Char,
  protected val originalLiteralToken: SyntaxElementType,
  private val canEscapeEolOrFramingSpaces: Boolean = false,
  private val additionalValidEscapes: String? = null,
  private val allowOctal: Boolean = true,
  private val allowHex: Boolean = false,
) : LexerBase() {
  private lateinit var buffer: CharSequence
  private var startOffset: Int = 0
  private var endOffset: Int = 0
  private var bufferEnd: Int = 0
  private var lastState: Int = 0
  private var state: Int = 0
  private var seenEscapedSpacesOnly = false

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.startOffset = startOffset
    state = if (quoteChar == NO_QUOTE_CHAR) AFTER_FIRST_QUOTE else initialState
    lastState = initialState
    bufferEnd = endOffset
    this.endOffset = locateToken(this.startOffset)
    seenEscapedSpacesOnly = true
  }

  override fun getState(): Int = lastState

  override fun getTokenType(): SyntaxElementType? {
    if (startOffset >= endOffset) return null

    if (buffer[startOffset] != '\\') {
      seenEscapedSpacesOnly = false
      return originalLiteralToken
    }

    if (startOffset + 1 >= endOffset) {
      return handleSingleSlashEscapeSequence()
    }

    val nextChar = buffer[startOffset + 1]
    seenEscapedSpacesOnly = seenEscapedSpacesOnly && nextChar == ' '

    if (
      canEscapeEolOrFramingSpaces && (
        nextChar == '\n' ||
        nextChar == ' ' && (
          seenEscapedSpacesOnly ||
          isTrailingSpace(startOffset + 2)))) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
    }

    if (nextChar == 'u') {
      return unicodeEscapeSequenceType
    }

    if (nextChar == 'x' && allowHex) {
      return hexCodedEscapeSeq
    }

    when (nextChar) {
      '0' -> {
        if (shouldAllowSlashZero) return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
      }
      in '1'..'7' -> {
        if (!allowOctal) return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
      }
      'n', 'r', 'b', 't',
      'f', '\'', '\"', '\\',
        -> return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
    }

    if (additionalValidEscapes != null && additionalValidEscapes.contains(nextChar, ignoreCase = false)) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
    }
    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN
  }

  protected open val shouldAllowSlashZero: Boolean get() = false

  protected open fun handleSingleSlashEscapeSequence(): SyntaxElementType = StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN

  // \xFF
  protected open val hexCodedEscapeSeq: SyntaxElementType get() = getStandardLimitedHexCodedEscapeSeq(4)

  // \uFFFF
  protected open val unicodeEscapeSequenceType: SyntaxElementType get() = getStandardLimitedHexCodedEscapeSeq(6)

  protected fun getStandardLimitedHexCodedEscapeSeq(offsetLimit: Int): SyntaxElementType {
    for (i in (this.startOffset + 2)..<(this.startOffset + offsetLimit)) {
      if (i >= this.endOffset || !this.buffer[i].isHexDigit()) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN
    }
    return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN
  }

  // all subsequent chars are escaped spaces
  private fun isTrailingSpace(start: Int): Boolean {
    var i = start
    while (i < bufferEnd) {
      val chr = buffer[i]
      when {
        chr != '\\' -> return false
        i == bufferEnd - 1 -> return false
        buffer[i + 1] != ' ' -> return false
      }
      i += 2
    }
    return true
  }

  override fun getTokenStart(): Int = startOffset
  override fun getTokenEnd(): Int = endOffset

  private fun locateToken(start: Int): Int {
    if (start == bufferEnd) {
      state = AFTER_LAST_QUOTE
    }
    if (state == AFTER_LAST_QUOTE) {
      return start
    }
    var i = start
    if (buffer[i] == '\\') {
      LOG.assertTrue(state != AFTER_LAST_QUOTE, this::toString)
      i++
      return when {
        i == bufferEnd || buffer[i] == '\n' && !canEscapeEolOrFramingSpaces -> {
          state = AFTER_LAST_QUOTE
          i
        }
        allowOctal && buffer[i] in '0'..'7' -> locateOctalEscapeSequence(i)
        allowHex && buffer[i] == 'x' -> locateHexEscapeSequence(start, i)
        buffer[i] == 'u' -> locateUnicodeEscapeSequence(start, i)
        else -> {
          val additionalLocation = locateAdditionalEscapeSequence(start, i)
          if (additionalLocation != -1) additionalLocation else i + 1
        }
      }
    }

    LOG.assertTrue(
      state == AFTER_LAST_QUOTE || buffer[i] == quoteChar,
      this::toString
    )
    while (i < bufferEnd) when {
      buffer[i] == '\\' -> return i
      state == AFTER_LAST_QUOTE && buffer[i] == quoteChar -> {
        if (i + 1 == bufferEnd) state = AFTER_LAST_QUOTE
        return i + 1
      }
      else -> {
        i++
        state = AFTER_FIRST_QUOTE
      }
    }

    return i
  }

  private fun locateOctalEscapeSequence(i: Int): Int {
    val first = buffer[i]
    var i = i + 1
    if (i < bufferEnd && buffer[i] in '0'..'7') {
      i++
      if (i < bufferEnd && first <= '3' && buffer[i] in '0'..'7') {
        i++
      }
    }
    return i
  }

  protected open fun locateHexEscapeSequence(start: Int, i: Int): Int {
    var i = i + 1
    while (i < start + 4) {
      when {
        i == bufferEnd ||
        buffer[i] == '\n' ||
        buffer[i] == quoteChar
          -> return i
        else -> i++
      }
    }
    return i
  }

  protected open fun locateUnicodeEscapeSequence(start: Int, i: Int): Int {
    var i = i + 1
    while (i < start + 6) {
      when {
        i == bufferEnd ||
        buffer[i] == '\n' ||
        buffer[i] == quoteChar
          -> return i
        else -> i++
      }
    }
    return i
  }

  protected open fun locateAdditionalEscapeSequence(start: Int, i: Int): Int = -1

  override fun advance() {
    lastState = state
    startOffset = endOffset
    endOffset = locateToken(startOffset)
  }

  override fun getBufferSequence(): CharSequence = buffer
  override fun getBufferEnd(): Int = bufferEnd

  companion object {
    private const val AFTER_FIRST_QUOTE = 1
    private const val AFTER_LAST_QUOTE = 2

    const val NO_QUOTE_CHAR: Char = (-1).toChar()

    private val LOG: Logger = noopLogger()  // TODO make the logger work???
  }
}

private inline fun Logger.assertTrue(condition: Boolean, message: (() -> String)): Boolean {
  if (!condition) {
    val message = buildString {
      append("Assertion failed")
      append(": ")
      append(message())
    }
    error(message)
  }
  return condition
}

@ApiStatus.Experimental
fun Char.isHexDigit(): Boolean = when (this) {
  in '0'..'9' -> true
  in 'a'..'f' -> true
  in 'A'..'F' -> true
  else -> false
}

@ApiStatus.Experimental
fun Char.isOctalDigit(): Boolean = this in '0'..'7'