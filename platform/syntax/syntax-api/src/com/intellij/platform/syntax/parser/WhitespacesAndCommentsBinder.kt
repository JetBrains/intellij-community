// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for defining custom element's edge processors for [SyntaxTreeBuilder.Marker].
 *
 * Each element has a pair of edge processors: for its left and right edge. An edge processor defines a position
 * of the element start and end in token stream with recognition of whitespace and comment tokens surrounding the element.
 *
 * @see SyntaxTreeBuilder.Marker.setCustomEdgeTokenBinders
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface WhitespacesAndCommentsBinder {
  /**
   * Provides an ability for the processor to get a text of any of given tokens.
   */
  interface TokenTextGetter {
    fun get(i: Int): CharSequence
  }

  /**
   * Analyzes whitespace and comment tokens at element's edge and returns element's edge position relative to these tokens.
   * Value returned by left edge processor will be used as a pointer to a first token of element.
   * Value returned by right edge processor will be used as a pointer to a token next of element's last token.
   *
   *
   *
   * Example 1: if a processor for left edge wants to leave all whitespaces and comments out of element's scope
   * (before it's start) it should return value of `tokens.size()` placing element's start pointer to a first
   * token after series of whitespaces/comments.
   *
   *
   *
   * Example 2: if a processor for right edge wants to leave all whitespaces and comments out of element's scope
   * (after its end) it should return value of `0` placing element's end pointer to a first
   * whitespace or comment after element's end.
   *
   * @param tokens       sequence of whitespace and comment tokens at the element's edge.
   * @param atStreamEdge `true` if sequence of tokens is located at the beginning or the end of token stream.
   * @param getter       token text getter.
   * @return position of element's edge relative to given tokens.
   */
  fun getEdgePosition(tokens: List<SyntaxElementType>, atStreamEdge: Boolean, getter: TokenTextGetter): Int

  /**
   * Recursive binder is allowed to adjust nested elements' positions.
   */
  fun isRecursive(): Boolean = false
}
