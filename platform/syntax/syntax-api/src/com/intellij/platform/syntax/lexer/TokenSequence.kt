// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.CancellationProvider
import kotlin.math.max

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

  fun assertMatches(text: CharSequence, lexer: Lexer, cancellationProvider: CancellationProvider?) {
    val sequence = Builder(text, lexer, cancellationProvider).performLexing()
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
) : Lexer {

  private var state: Int = 0

  override fun getState(): Int = state

  fun startMeasured(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    // todo find a way to perform lexing with logging
    //if (!LOG.isDebugEnabled()) {
    start(buffer, startOffset, endOffset, initialState)
    //return
    //}
    //val start = System.currentTimeMillis()
    //start(buffer, startOffset, endOffset, initialState)
    //val startDuration = System.currentTimeMillis() - start
    //if (startDuration > LEXER_START_THRESHOLD) {
    //  LOG.debug("Starting lexer took: ", startDuration,
    //            "; at ", startOffset, " - ", endOffset, "; state: ", initialState,
    //            "; text: ", StringUtil.shortenTextWithEllipsis(buffer.toString(), 1024, 500)
    //  )
    //}
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
        // todo add logging here!!!
        //reportDescendingOffsets(i, offset, tokenStart)
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

  /*
  fun reportDescendingOffsets(tokenIndex: Int, offset: Int, tokenStart: Int) {
    val sb: @NonNls StringBuilder = StringBuilder()
    val tokenType = myLexer.getTokenType()
    sb.append("Token sequence broken")
      .append("\n  this: '").append(myLexer.getTokenText()).append("' (").append(tokenType).append(
        ':') //.append(tokenType != null ? tokenType.getLanguage() : null).append(") ").append(tokenStart).append(":") todo
      .append(myLexer.getTokenEnd())
    if (tokenIndex > 0) {
      val prevStart = myLexStarts[tokenIndex - 1]
      sb.append("\n  prev: '").append(myText.subSequence(prevStart, offset)).append("' (").append(myLexTypes[tokenIndex - 1]).append(':')
      //.append(myLexTypes[tokenIndex - 1].getLanguage()).append(") ").append(prevStart).append(":").append(offset) todo
    }
    val quoteStart = max((tokenStart - 256).toDouble(), 0.0).toInt()
    val quoteEnd = min((tokenStart + 256).toDouble(), myText.length.toDouble()).toInt()
    sb.append("\n  quote: [").append(quoteStart).append(':').append(quoteEnd)
      .append("] '").append(myText.subSequence(quoteStart, quoteEnd)).append('\'')
    LOG.error(sb.toString())
  }
  */

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
