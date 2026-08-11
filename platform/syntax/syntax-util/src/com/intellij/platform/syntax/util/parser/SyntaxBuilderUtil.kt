// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.parser

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesBinders
import com.intellij.util.text.CharSequenceSubSequence
import kotlin.jvm.JvmStatic

object SyntaxBuilderUtil {
  /**
   * Advances lexer by given number of tokens (but not beyond the end of token stream).
   *
   * @param this PSI builder to operate on.
   * @param count number of tokens to skip.
   */
  @JvmStatic
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
  @JvmStatic
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
  @JvmStatic
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
  @JvmStatic
  fun drop(vararg markers: SyntaxTreeBuilder.Marker?) {
    for (marker in markers) {
      marker?.drop()
    }
  }

  @JvmStatic
  fun SyntaxTreeBuilder.rawTokenText(index: Int): CharSequence {
    return CharSequenceSubSequence(
      baseSequence = text,
      start = rawTokenTypeStart(index),
      end = rawTokenTypeStart(index + 1)
    )
  }

  /**
   * Builds a shallow marker which starts with [leftBrace] and ends with [rightBrace].
   *
   * If [leftBrace] is missing, returns `null`.
   * If [rightBrace] is missing, consumes everything until EOF.
   *
   * @return collapsed marker of the block or `null` if [leftBrace] is missing.
   */
  @JvmStatic
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
   * Checks if [text] looks like a balanced block:
   * starts with [leftBrace], ends with [rightBrace] as the last token,
   * and brace balance is maintained throughout.
   *
   * @param text - text to check
   * @param lexer - lexer to use
   * @param leftBrace - left brace element type
   * @param rightBrace - right brace element type
   * @param cancellationProvider - a hook to stop operation if it's not necessary anymore
   * @return true if [text] forms a balanced block
   */
  @JvmStatic
  fun isBalancedBlock(
    text: CharSequence,
    lexer: Lexer,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean {
    lexer.start(text)
    return checkBraceBalance(
      leftBrace = leftBrace,
      rightBrace = rightBrace,
      cancellationProvider = cancellationProvider,
      advance = lexer::advance,
      curType = lexer::getTokenType,
      requireOuterBraces = true,
    )
  }

  /**
   * Checks that [leftBrace]/[rightBrace] are balanced inside the [tokenList]:
   * equal count and balance never goes negative.
   *
   * Unlike [isBalancedBlock], this does **not** require the first/last token
   * to be the brace pair — it only checks internal balance.
   */
  @JvmStatic
  fun areBracesBalancedInside(
    tokenList: TokenList,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean {
    var i = 0
    return checkBraceBalance(
      leftBrace = leftBrace,
      rightBrace = rightBrace,
      cancellationProvider = cancellationProvider,
      advance = { i++ },
      curType = { tokenList.getTokenType(i) },
      requireOuterBraces = false,
    )
  }

  /**
   * Checks if [tokenList] looks like a balanced block:
   * starts with [leftBrace], ends with [rightBrace] as the last token,
   * and brace balance is maintained throughout.
   *
   * @param tokenList - tokens to check
   * @param leftBrace - left brace element type
   * @param rightBrace - right brace element type
   * @param cancellationProvider - a hook to stop operation if it's not necessary anymore
   * @return true if [tokenList] forms a balanced block
   */
  @JvmStatic
  fun isBalancedBlock(
    tokenList: TokenList,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean {
    var i = 0
    return checkBraceBalance(
      leftBrace = leftBrace,
      rightBrace = rightBrace,
      cancellationProvider = cancellationProvider,
      advance = { i++ },
      curType = { tokenList.getTokenType(i) },
      requireOuterBraces = true,
    )
  }

  // region deprecated

  @Deprecated(
    "Use isBalancedBlock instead",
    ReplaceWith("isBalancedBlock(text, lexer, leftBrace, rightBrace, cancellationProvider)"),
  )
  @JvmStatic
  fun hasProperBraceBalance(
    text: CharSequence,
    lexer: Lexer,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean = isBalancedBlock(text, lexer, leftBrace, rightBrace, cancellationProvider)

  @Deprecated(
    "Use isBalancedBlock instead",
    ReplaceWith("isBalancedBlock(tokenList, leftBrace, rightBrace, cancellationProvider)"),
  )
  @JvmStatic
  fun hasProperBraceBalance(
    tokenList: TokenList,
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
  ): Boolean = isBalancedBlock(tokenList, leftBrace, rightBrace, cancellationProvider)

  // endregion

  private inline fun checkBraceBalance(
    leftBrace: SyntaxElementType,
    rightBrace: SyntaxElementType,
    cancellationProvider: CancellationProvider?,
    advance: () -> Unit,
    curType: () -> SyntaxElementType?,
    requireOuterBraces: Boolean,
  ): Boolean {
    var balance: Int
    if (requireOuterBraces) {
      if (curType() !== leftBrace) return false
      advance()
      balance = 1
    }
    else {
      balance = 0
    }

    var i = 0
    while (true) {
      if (i % 100 == 0) {
        cancellationProvider?.checkCancelled()
      }
      i++

      val type = curType()

      if (type == null) {
        return balance == 0
      }

      if (requireOuterBraces && balance == 0) {
        return false // the last brace is not the last token
      }

      if (type === leftBrace) {
        balance++
      }
      else if (type === rightBrace) {
        balance--
        if (!requireOuterBraces && balance < 0) {
          return false
        }
      }

      advance()
    }
  }
}