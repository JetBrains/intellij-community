// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class MatcherWithFallback internal constructor(
  private val myMainMatcher: MinusculeMatcher,
  private val myFallbackMatcher: MinusculeMatcher?,
) : MinusculeMatcher() {
  override val pattern: String
    get() = myMainMatcher.pattern

  override fun matches(name: String): Boolean {
    return myMainMatcher.matches(name) ||
           myFallbackMatcher != null && myFallbackMatcher.matches(name)
  }

  override fun match(name: String): List<TextRange>? {
    val mainRanges = myMainMatcher.match(name)
    val useMainRanges = !mainRanges.isNullOrEmpty() || myFallbackMatcher == null
    return if (useMainRanges) mainRanges else myFallbackMatcher.match(name)
  }

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? {
    return match(name)?.asReversed()?.let(FList<TextRange>::createFromReversed)
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean): Int {
    val mainRanges = myMainMatcher.match(name)
    val useMainRanges = !mainRanges.isNullOrEmpty() || myFallbackMatcher == null

    return if (useMainRanges) {
      myMainMatcher.matchingDegree(name, valueStartCaseMatch, mainRanges)
    }
    else {
      myFallbackMatcher.matchingDegree(name, valueStartCaseMatch)
    }
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<TextRange>?): Int {
    val mainRanges = myMainMatcher.match(name)
    val useMainRanges = !mainRanges.isNullOrEmpty() || myFallbackMatcher == null

    return if (useMainRanges) {
      myMainMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    }
    else {
      myFallbackMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    }
  }

  @Deprecated("use matchingDegree(String, Boolean, List<TextRange>)", replaceWith = ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)"))
  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)
  }

  override fun toString(): String {
    return "MatcherWithFallback{" +
           "myMainMatcher=" + myMainMatcher +
           ", myFallbackMatcher=" + myFallbackMatcher +
           '}'
  }
}
