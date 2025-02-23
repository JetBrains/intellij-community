// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.CancellationProvider
import org.jetbrains.annotations.ApiStatus

/**
 * This class represents the result of lexing: text and the tokens produced from it by some lexer.
 * It allows clients to inspect all tokens at once and easily move back and forward to implement some simple lexer-based checks.
 */
@ApiStatus.Experimental
interface TokenList {
  /**
   * @return the number of tokens inside
   */
  val tokenCount: Int

  /**
   * @return the full text that was split into the tokens represented here
   */
  val tokenizedText: CharSequence

  /**
   * @return the start offset of the token with the given index
   */
  fun getTokenStart(index: Int): Int

  /**
   * @return the end offset of the token with the given index
   */
  fun getTokenEnd(index: Int): Int

  /**
   * @return the type of the token with the given index, or null if the index is negative or exceeds token count
   */
  fun getTokenType(index: Int): SyntaxElementType?

  /**
   * @return the text of the token with the given index, or null if the index is negative or exceeds token count
   */
  fun getTokenText(index: Int): CharSequence? {
    if (index < 0 || index >= tokenCount) return null
    return tokenizedText.subSequence(getTokenStart(index), getTokenEnd(index))
  }

  /**
   * @return whether [.getTokenType](index) would return the given type
   */
  fun hasType(index: Int, type: SyntaxElementType): Boolean {
    return getTokenType(index) == type
  }

  /**
   * @return whether [.getTokenType](index) would return any of the given types (null acceptable, indicating start or end of the text)
   */
  fun hasType(index: Int, vararg types: SyntaxElementType): Boolean {
    return getTokenType(index) in types
  }

  /**
   * @return whether [.getTokenType](index) would return a type in the given set
   */
  fun hasType(index: Int, types: Set<SyntaxElementType>): Boolean {
    return types.contains(getTokenType(index))
  }

  /**
   * Moves back, potentially skipping tokens which represent a valid nesting sequence
   * with the given types for opening and closing braces.
   * @return an index `prev` of a token before `index` such that either:
   *
   *  1. `prev == index - 1`
   *  1. `hasType(prev + 1, opening) && hasType(index, closing)` and every opening brace between those indices has its closing one before `index`
   *
   */
  fun backWithBraceMatching(index: Int, opening: SyntaxElementType, closing: SyntaxElementType): Int {
    var index = index
    if (getTokenType(index) == closing) {
      var nesting = 1
      while (nesting > 0 && index > 0) {
        index--
        val type = getTokenType(index)
        if (type == closing) {
          nesting++
        }
        else if (type == opening) {
          nesting--
        }
      }
    }
    return index - 1
  }

  /**
   * Moves back from `index` while tokens belong to the given set
   * @return the largest `prev <= index` whose token type doesn't belong to `toSkip`
   */
  fun backWhile(index: Int, toSkip: Set<SyntaxElementType>): Int {
    var index = index
    while (hasType(index, toSkip)) {
      index--
    }
    return index
  }

  /**
   * Moves forward from `index` while tokens belong to the given set
   * @return the smallest `next >= index` whose token type doesn't belong to `toSkip`
   */
  fun forwardWhile(index: Int, toSkip: Set<SyntaxElementType>): Int {
    var index = index
    while (hasType(index, toSkip)) {
      index++
    }
    return index
  }
}

fun performLexing(text: CharSequence, lexer: Lexer, cancellationProvider: CancellationProvider? = null): TokenList {
  if (lexer is TokenListLexerImpl) {
    val existing = lexer.tokens
    if (existing is TokenSequence && equal(text, existing.tokenizedText)) {
      // prevent clients like PsiBuilder from modifying shared token types
      return TokenSequence(existing.lexStarts,
                           existing.lexTypes.clone(),
                           existing.tokenCount,
                           text) as TokenList
    }
  }
  val sequence = Builder(text, lexer, cancellationProvider).performLexing()
  return sequence as TokenList
}

fun TokenList(
  lexStarts: IntArray,
  lexTypes: Array<SyntaxElementType>,
  tokenCount: Int,
  tokenizedText: CharSequence,
): TokenList = TokenSequence(lexStarts, lexTypes, tokenCount, tokenizedText)

fun tokenListLexer(tokenList: TokenList): Lexer {
  return TokenListLexerImpl(tokenList)
}
