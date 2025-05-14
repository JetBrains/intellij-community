// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.parser

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import com.intellij.util.text.CharSequenceSubSequence
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmStatic

@ApiStatus.Experimental
object SyntaxBuilderUtil {
  /**
   * Advances lexer by given number of tokens (but not beyond the end of token stream).
   *
   * @param this PSI builder to operate on.
   * @param count number of tokens to skip.
   */
  fun SyntaxTreeBuilder.advance(count: Int) {
    repeat(count) {
      if (eof()) return
      tokenType // ensure token is processed
      advanceLexer()
    }
  }

  /**
   * Advances lexer if current token is of expected type, does nothing otherwise.
   *
   * @param this PSI builder to operate on.
   * @param expectedType expected token.
   * @return true if token matches, false otherwise.
   */
  fun SyntaxTreeBuilder.expect(expectedType: SyntaxElementType?): Boolean {
    if (tokenType === expectedType) {
      advanceLexer()
      return true
    }
    return false
  }

  /**
   * Advances lexer if current token is of expected type, does nothing otherwise.
   *
   * @param this@expect PSI builder to operate on.
   * @param expectedTypes expected token types.
   * @return true if token matches, false otherwise.
   */
  fun SyntaxTreeBuilder.expect(expectedTypes: SyntaxElementTypeSet): Boolean {
    if (tokenType in expectedTypes) {
      advanceLexer()
      return true
    }
    return false
  }

  /**
   * Release group of allocated markers.
   *
   * @param markers markers to drop.
   */
  fun drop(vararg markers: SyntaxTreeBuilder.Marker?) {
    for (marker in markers) {
      marker?.drop()
    }
  }

  fun SyntaxTreeBuilder.rawTokenText(index: Int): CharSequence {
    return CharSequenceSubSequence(
      baseSequence = text,
      start = rawTokenTypeStart(index),
      end = rawTokenTypeStart(index + 1)
    )
  }

  /**
   * tries to parse a code block with corresponding left and right braces.
   * @return collapsed marker of the block or `null` if there is no code block at all.
   */
  fun SyntaxTreeBuilder.parseBlockLazy(
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    codeBlock: SyntaxElementType,
  ): SyntaxTreeBuilder.Marker? {
    if (tokenType !== leftBrace) return null

    val marker = mark()

    advanceLexer()

    var braceCount = 1

    while (braceCount > 0 && !eof()) {
      val tokenType = tokenType
      if (tokenType === leftBrace) {
        braceCount++
      }
      else if (tokenType === rightBrace) {
        braceCount--
      }
      advanceLexer()
    }

    marker.collapse(codeBlock)

    if (braceCount > 0) {
      marker.setCustomEdgeTokenBinders(null, WhitespacesBinders.greedyRightBinder())
    }

    return marker
  }

  /**
   * Checks if `text` looks like a proper block.
   * In particular it
   * (1) checks brace balance
   * (2) verifies that the block's closing brace is the last token
   *
   * @param text - text to check
   * @param lexer - lexer to use
   * @param leftBrace - left brace element type
   * @param rightBrace - right brace element type
   * @param cancellationProvider - a hook to stop operation if it's not necessary anymore
   * @return true if `text` passes the checks
   */
  @JvmStatic
  fun hasProperBraceBalance(
    text: CharSequence,
    lexer: Lexer,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean {
    lexer.start(text)

    if (lexer.getTokenType() !== leftBrace) return false

    lexer.advance()
    var balance = 1

    while (true) {
      cancellationProvider?.checkCancelled()
      val type = lexer.getTokenType()

      if (type == null) {
        //eof: checking balance
        return balance == 0
      }

      if (balance == 0) {
        //the last brace is not the last token
        return false
      }

      if (type === leftBrace) {
        balance++
      }
      else if (type === rightBrace) {
        balance--
      }

      lexer.advance()
    }
  }
}