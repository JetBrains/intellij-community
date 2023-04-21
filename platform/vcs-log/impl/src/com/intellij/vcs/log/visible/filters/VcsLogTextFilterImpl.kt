// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.util.TextRange
import com.intellij.vcs.log.VcsLogDetailsFilter
import org.jetbrains.annotations.NonNls

/**
 * @see VcsLogFilterObject.fromPattern
 */
internal data class VcsLogTextFilterImpl(private val text: String,
                                         private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilterWithMatches {

  override fun matches(message: String): Boolean = message.contains(text, !isMatchCase)

  override fun getText(): String = text

  override fun isRegex(): Boolean = false

  override fun matchesCase(): Boolean = isMatchCase

  override fun matchingRanges(message: String): Iterable<TextRange> {
    return generateSequence({ findMatchingRange(message, null) }) {
      lastRange -> findMatchingRange(message, lastRange)
    }.asIterable()
  }

  private fun findMatchingRange(message: String, previousRange: TextRange?): TextRange? {
    val startIndex = previousRange?.endOffset ?: 0
    val startOffset = message.indexOf(text, startIndex, !isMatchCase).takeIf { it >= 0 } ?: return null
    return TextRange(startOffset, startOffset + text.length)
  }

  @NonNls
  override fun toString(): String {
    return "containing '$text' ${caseSensitiveText()}"
  }
}