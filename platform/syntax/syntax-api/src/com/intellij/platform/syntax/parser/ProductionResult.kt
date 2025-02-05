// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.impl.builder.ParsingTreeBuilder
import com.intellij.platform.syntax.lexer.TokenList
import org.jetbrains.annotations.ApiStatus

/**
 * Call this function after you're done with the parsing to obtain the result
 */
@ApiStatus.Experimental
fun prepareProduction(builder: SyntaxTreeBuilder): ProductionResult {
  return (builder as ParsingTreeBuilder).prepareProduction()
}

/**
 * Raw result of parsing
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProductionResult {
  /**
   * Result of lexing
   * Note that it can be amended during parsing (see [SyntaxTreeBuilder.remapCurrentToken] and [SyntaxTreeBuilder.Marker]
   */
  val tokenSequence: TokenList

  /**
   * List of markers defining the resulting syntax tree
   */
  val productionMarkers: ProductionMarkerList

  /**
   * @return fills [dest] with the token starts in an [IntArray].
   * Note, that the expected max length of the array is [tokenSequence].size + 1. The last item stores the end offset of the last token.
   */
  fun copyTokenStartsToArray(dest: IntArray, srcStart: Int, destStart: Int, length: Int)

  /**
   * fills [dest] with the token types.
   */
  fun copyTokenTypesToArray(dest: Array<in SyntaxElementType>, srcStart: Int, destStart: Int, length: Int)
}

/**
 * List of markers defining the resulting syntax tree
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProductionMarkerList {
  /**
   * @return the production marker at [index] position
   */
  fun getMarker(index: Int): SyntaxTreeBuilder.Production

  /**
   * @return true if the producion marker at position [index] is a done marker and false if it's a start marker.
   */
  fun isDoneMarker(index: Int): Boolean

  /**
   * Number of markers in the list
   */
  val size: Int
}