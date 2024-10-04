// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogTextFilter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.regex.Pattern

@ApiStatus.Internal
@ApiStatus.Experimental
interface VcsLogTextFilterWithMatches : VcsLogTextFilter {
  override fun matches(message: String): Boolean {
    return matchingRanges(message).iterator().hasNext()
  }

  /**
   * Returns text ranges for matches in the specified commit message.
   *
   * @param message a commit message to match
   * @return an Iterable containing text ranges for matches
   */
  fun matchingRanges(message: String): Iterable<TextRange>
}

/**
 * @see VcsLogFilterObject.fromPattern
 */
internal data class VcsLogRegexTextFilter(private val pattern: Pattern) : VcsLogDetailsFilter, VcsLogTextFilterWithMatches {
  override fun matches(message: String): Boolean = pattern.matcher(message).find()

  override fun getText(): String = pattern.pattern()

  override fun isRegex(): Boolean = true

  override fun matchesCase(): Boolean = (pattern.flags() and Pattern.CASE_INSENSITIVE) == 0

  override fun matchingRanges(message: String): Iterable<TextRange> {
    return Iterable { pattern.matcher(message).results().map { TextRange(it.start(), it.end()) }.iterator() }
  }

  @NonNls
  override fun toString(): String {
    return "matching '$text' ${caseSensitiveText()}"
  }
}

/**
 * @see VcsLogFilterObject.fromPatternsList
 */
internal class VcsLogMultiplePatternsTextFilter(val patterns: List<String>,
                                                private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilterWithMatches {
  override fun getText(): String = if (patterns.size == 1) patterns.single() else patterns.joinToString("|") { Pattern.quote(it) }

  override fun isRegex(): Boolean = patterns.size > 1

  override fun matchesCase(): Boolean = isMatchCase

  override fun matches(message: String): Boolean = patterns.any { message.contains(it, !isMatchCase) }

  override fun matchingRanges(message: String): Iterable<TextRange> {
    return generateSequence({ findNextMatch(message, null) }) { previousRange ->
      findNextMatch(message, previousRange)
    }.asIterable()
  }

  private fun findNextMatch(message: String, previousRange: TextRange?): TextRange? {
    val startIndex = previousRange?.endOffset ?: 0

    var match: TextRange? = null
    for (pattern in patterns) {
      val patternIndex = message.indexOf(pattern, startIndex, !isMatchCase)
      if (patternIndex < 0) continue

      if (match == null || patternIndex <= match.startOffset) {
        match = TextRange(patternIndex, patternIndex + pattern.length)
      }
    }
    return match
  }

  @NonNls
  override fun toString(): String {
    return "containing at least one of the ${patterns.joinToString(", ") { s -> "'$s'" }} ${caseSensitiveText()}"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VcsLogMultiplePatternsTextFilter

    return Comparing.haveEqualElements(patterns, other.patterns) && isMatchCase == other.isMatchCase
  }

  override fun hashCode(): Int {
    return Objects.hash(Comparing.unorderedHashcode(patterns), isMatchCase)
  }
}

@NonNls
internal fun VcsLogTextFilter.caseSensitiveText() = "(case ${if (matchesCase()) "sensitive" else "insensitive"})"