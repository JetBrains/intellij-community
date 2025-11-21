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

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<TextRange>?): Int {
    return delegate.matchingDegree(name, valueStartCaseMatch, fragments)
  }

  @Deprecated("use matchingDegree(String, Boolean, List<TextRange>)", replaceWith = ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)"))
  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    return delegate.matchingDegree(name, valueStartCaseMatch, fragments)
  }

  override fun match(name: String): List<TextRange>? {
    var match = delegate.match(name)
    return if (!match.isNullOrEmpty()) {
      val allMatchesReversed = mutableListOf<List<TextRange>>()
      var lastOffset = 0
      while (!match.isNullOrEmpty()) {
        val matchWithAbsoluteOffset = match.map { it.shiftRight(lastOffset) }
        allMatchesReversed.add(matchWithAbsoluteOffset)
        lastOffset = matchWithAbsoluteOffset.last().endOffset
        match = delegate.match(name.substring(lastOffset))
      }
      allMatchesReversed.flatten()
    }
    else {
      match
    }
  }

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? {
    return match(name)?.asReversed()?.let(FList<TextRange>::createFromReversed)
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
