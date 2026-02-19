// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList

/**
 * A matched fragment in text matching.
 *
 * Uses half-open interval `[startOffset, endOffset)`:
 * - [startOffset] is inclusive (first matched character)
 * - [endOffset] is exclusive (one past last matched character)
 *
 * `MatchedFragment(2, 5)` covers characters at indices 2, 3, 4.
 *
 * For typo-tolerant matching, [errorCount] tracks keyboard mistakes in this fragment:
 * - Typo: wrong adjacent key (pattern "get" matches "grt", E is next to R)
 * - Swap: two chars swapped (pattern "the" matches "teh")
 * - Miss: skipped character (pattern "the" matches "te")
 *
 * Higher [errorCount] means lower match score.
 *
 * @property startOffset inclusive start index
 * @property endOffset exclusive end index
 * @property errorCount number of typos in this fragment (0 for exact match)
 */
data class MatchedFragment(val startOffset: Int, val endOffset: Int, val errorCount: Int = 0) {
  init {
    require(startOffset <= endOffset) { "startOffset=$startOffset > endOffset=$endOffset" }
  }

  val length: Int
    get() = endOffset - startOffset
}

internal fun FList<out TextRange>.undeprecate(): List<MatchedFragment> {
  return map { MatchedFragment(it.startOffset, it.endOffset) }
}

internal fun List<MatchedFragment>.deprecated(): FList<TextRange> {
  return asReversed().asSequence().map { TextRange(it.startOffset, it.endOffset) }.fold(FList.emptyList()) { acc, textRange -> acc.prepend(textRange) }
}