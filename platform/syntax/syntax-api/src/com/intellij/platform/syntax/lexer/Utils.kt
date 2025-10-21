// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class TokenListBuilder internal constructor() {
  private val tokens = mutableListOf<Pair<String, SyntaxElementType>>()

  fun token(tokenText: String, tokenType: SyntaxElementType) {
    tokens.add(Pair(tokenText, tokenType))
  }

  internal fun build(): TokenList {
    if (tokens.isEmpty()) return EmptyTokenList

    val tokenizedText = tokens.joinToString("") { it.first }
    val lexStarts = run {
      var currentOffset = 0
      IntArray(tokens.size + 1) {
        currentOffset.also {
          tokens.getOrNull(currentOffset + it)?.let { currentOffset += it.first.length }
        }
      }
    }
    val lexTypes = Array(tokens.size + 1) { tokens.getOrNull(it)?.second }
    return TokenList(
      lexStarts, lexTypes as Array<SyntaxElementType>, tokens.size, tokenizedText
    )
  }
}

private data object EmptyTokenList : TokenList {
  override val tokenCount: Int = 0
  override val tokenizedText: CharSequence = ""

  override fun getTokenStart(index: Int): Int = error("Token list is empty.")
  override fun getTokenEnd(index: Int): Int = error("Token list is empty.")
  override fun getTokenType(index: Int): SyntaxElementType? = null

  override fun slice(start: Int, end: Int): TokenList =
    if (start == 0 && end == 0) this
    else error("Token list is empty. You may only call `slice(0,0)`.")

  override fun remap(index: Int, newValue: SyntaxElementType) = error("Can't remap in an empty token list.")
}

fun buildTokenList(builderFunc: TokenListBuilder.() -> Unit): TokenList =
  TokenListBuilder().apply(builderFunc).build()

/**
 * Returns an index to a token at a given offset in text.
 */
fun TokenList.tokenIndexAtOffset(charIndex: Int): Int {
  for (i in 0..<tokenCount) {
    if (getTokenStart(i) <= charIndex && charIndex < getTokenEnd(i)) {
      return i
    }
  }
  return -1
}
