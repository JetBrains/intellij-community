// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.MatchingMode
import kotlin.jvm.JvmStatic

/**
 * [FixingLayoutMatcher] extension that returns all matches (not just the first one)
 * from [MinusculeMatcher.matchingFragments].
 */
class AllOccurrencesMatcher private constructor(
  pattern: String,
  matchingMode: MatchingMode,
  hardSeparators: String,
  keyboardLayoutConverter: KeyboardLayoutConverter,
) : MinusculeMatcher() {
  @Deprecated("Use {@link #AllOccurrencesMatcher(String, MatchingCaseSensitivity, String, KeyboardLayoutConverter)} instead")
  constructor(pattern: String,
              options: NameUtil.MatchingCaseSensitivity,
              hardSeparators: String) : this(pattern, options.matchingMode(), hardSeparators, PlatformKeyboardLayoutConverter)

  private val delegate: MinusculeMatcher = FixingLayoutMatcher(pattern, matchingMode, hardSeparators, keyboardLayoutConverter)

  override val pattern: String
    get() = delegate.pattern

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    return delegate.matchingDegree(name, valueStartCaseMatch, fragments)
  }

  override fun matchingFragments(name: String): FList<TextRange>? {
    var match = delegate.matchingFragments(name)
    return if (!match.isNullOrEmpty()) {
      val allMatchesReversed = mutableListOf<FList<TextRange>>()
      var lastOffset = 0
      while (!match.isNullOrEmpty()) {
        val reversedWithAbsoluteOffsets = match.fold(FList.emptyList<TextRange>()) { acc, range -> acc.prepend(range.shiftRight(lastOffset)) }
        allMatchesReversed.add(reversedWithAbsoluteOffsets)
        lastOffset = reversedWithAbsoluteOffsets.first().endOffset
        match = delegate.matchingFragments(name.substring(lastOffset))
      }
      allMatchesReversed.reversed().flatten().fold(FList.emptyList()) { acc, range -> acc.prepend(range) }
    }
    else {
      match
    }
  }

  override fun toString(): String {
    return "AllOccurrencesMatcher{" +
           "delegate=" + delegate +
           '}'
  }

  companion object {
    @JvmStatic
    fun create(pattern: String, matchingMode: MatchingMode, hardSeparators: String, keyboardLayoutConverter: KeyboardLayoutConverter): MinusculeMatcher {
      return AllOccurrencesMatcher(pattern, matchingMode, hardSeparators, keyboardLayoutConverter)
    }

    @Deprecated("Use {@link #create(String, MatchingCaseSensitivity, String)} instead")
    @JvmStatic
    fun create(pattern: String, options: NameUtil.MatchingCaseSensitivity, hardSeparators: String): MinusculeMatcher {
      return AllOccurrencesMatcher(pattern, options.matchingMode(), hardSeparators, PlatformKeyboardLayoutConverter)
    }
  }
}
