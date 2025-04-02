// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.Logger
import org.jetbrains.annotations.NonNls
import kotlin.math.max
import kotlin.math.min

internal class TokenSequence(
  internal val lexStarts: IntArray,
  internal val lexTypes: Array<SyntaxElementType>,
  override val tokenCount: Int,
  override val tokenizedText: CharSequence,
) : TokenList {
  init {
    assert(tokenCount < lexStarts.size)
    assert(tokenCount < lexTypes.size)
  }

  fun assertMatches(
    text: CharSequence,
    lexer: Lexer,
    cancellationProvider: CancellationProvider?,
    logger: Logger?,
  ) {
    val sequence = Builder(text, lexer, cancellationProvider, logger).performLexing()
    assert(tokenCount == sequence.tokenCount)
    for (j in 0..tokenCount) {
      if (sequence.lexStarts[j] != lexStarts[j] || sequence.lexTypes[j] !== lexTypes[j]) {
        assert(false)
      }
    }
  }

  override fun getTokenType(index: Int): SyntaxElementType? {
    if (index < 0 || index >= tokenCount) return null
    return lexTypes[index]
  }

  override fun getTokenStart(index: Int): Int {
    return lexStarts[index]
  }

  override fun getTokenEnd(index: Int): Int {
    return lexStarts[index + 1]
  }
}

/**
 * A simple lexer over [TokenList].
 * [performLexing] is optimized for using it.
 */
internal class TokenListLexerImpl(
  val tokens: TokenList,
  val logger: Logger?,
) : Lexer {

  private var state: Int = 0

  override fun getState(): Int = state

  fun startMeasured(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    if (logger?.isDebugEnabled() != true) {
      start(buffer, startOffset, endOffset, initialState)
      return
    }

    val start = System.currentTimeMillis()
    start(buffer, startOffset, endOffset, initialState)
    val startDuration = System.currentTimeMillis() - start
    if (startDuration > LEXER_START_THRESHOLD) {
      logger.debug("Starting lexer took: $startDuration; at $startOffset - $endOffset; state: $initialState; text: ${buffer.shortenTextWithEllipsis(1024, 500)}")
    }
  }

  override fun start(buf: CharSequence, start: Int, end: Int) {
    startMeasured(buf, start, end, 0)
  }

  override fun start(buf: CharSequence) {
    startMeasured(buf, 0, buf.length, 0)
  }

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    assert(equal(buffer, tokens.tokenizedText))
    assert(startOffset == 0)
    assert(endOffset == buffer.length)
    assert(initialState == 0)
    state = 0
  }

  override fun getTokenType(): SyntaxElementType? {
    return tokens.getTokenType(getState())
  }

  override fun getTokenStart(): Int {
    return tokens.getTokenStart(getState())
  }

  override fun getTokenEnd(): Int {
    return tokens.getTokenEnd(getState())
  }

  override fun advance() {
    state++
  }

  override fun getBufferSequence(): CharSequence {
    return tokens.tokenizedText
  }

  override fun getBufferEnd(): Int {
    return tokens.tokenizedText.length
  }

  override fun getCurrentPosition(): LexerPosition {
    val offset = getTokenStart()
    val intState = getState()
    return LexerPositionImpl(offset, intState)
  }

  override fun restore(position: LexerPosition) {
    start(getBufferSequence(), position.offset, getBufferEnd(), position.state)
  }
}

internal class Builder(
  val text: CharSequence,
  val lexer: Lexer,
  val cancellationProvider: CancellationProvider?,
  val logger: Logger?,
) {
  private var lexStarts: IntArray
  private var lexTypes: Array<SyntaxElementType?>

  init {
    val approxLexCount = max(10.0, (text.length / 5).toDouble()).toInt()

    lexStarts = IntArray(approxLexCount)
    lexTypes = arrayOfNulls(approxLexCount)
  }

  fun performLexing(): TokenSequence {
    lexer.start(text)
    var i = 0
    var offset = 0
    while (true) {
      val type = lexer.getTokenType()
      if (type == null) break

      if (i % 20 == 0) cancellationProvider?.checkCancelled()

      if (i >= lexTypes.size - 1) {
        resizeLexemes(i * 3 / 2)
      }
      val tokenStart = lexer.getTokenStart()
      if (tokenStart < offset) {
        reportDescendingOffsets(i, offset, tokenStart)
      }
      offset = tokenStart
      lexStarts[i] = offset
      lexTypes[i] = type
      i++
      lexer.advance()
    }

    lexStarts[i] = text.length

    return TokenSequence(lexStarts, lexTypes as Array<SyntaxElementType>, i, text)
  }

  private fun reportDescendingOffsets(tokenIndex: Int, offset: Int, tokenStart: Int) {
    if (logger == null) return

    val sb: @NonNls StringBuilder = StringBuilder()
    val tokenType = lexer.getTokenType()
    sb.append("Token sequence broken")
      .append("\n  this: '").append(lexer.getTokenText()).append("' (").append(tokenType).append(") ").append(tokenStart).append(":")
      .append(lexer.getTokenEnd())

    if (tokenIndex > 0) {
      val prevStart = lexStarts[tokenIndex - 1]
      sb.append("\n  prev: '").append(text.subSequence(prevStart, offset)).append("' (").append(lexTypes[tokenIndex - 1]).append(") ").append(prevStart).append(":").append(offset)
    }
    val quoteStart = max(tokenStart - 256, 0)
    val quoteEnd = min(tokenStart + 256, text.length)
    sb.append("\n  quote: [").append(quoteStart).append(':').append(quoteEnd).append("] '").append(text.subSequence(quoteStart, quoteEnd)).append('\'')
    logger.error(sb.toString())
  }

  fun resizeLexemes(newSize: Int) {
    lexStarts = lexStarts.copyOf(newSize)
    lexTypes = lexTypes.copyOf(newSize)
  }
}

private class LexerPositionImpl(
  override val offset: Int,
  override val state: Int,
) : LexerPosition

private const val LEXER_START_THRESHOLD: Long = 500

internal fun equal(s1: CharSequence, s2: CharSequence): Boolean {
  if (s1 === s2) return true
  if (s1.length != s2.length) return false

  return (0..<s1.length).all { i ->
    s1[i] == s2[i]
  }
}

private fun CharSequence.shortenTextWithEllipsis(
  maxLength: Int,
  suffixLength: Int,
): CharSequence {
  val symbol = "..."
  val textLength = length
  if (textLength <= maxLength) {
    return this
  }

  val prefixLength = maxLength - suffixLength - symbol.length
  assert(prefixLength >= 0)
  return substring(0, prefixLength) + symbol + substring(textLength - suffixLength)
}

