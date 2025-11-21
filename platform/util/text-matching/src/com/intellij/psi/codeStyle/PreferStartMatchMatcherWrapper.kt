// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList

class PreferStartMatchMatcherWrapper(private val myDelegateMatcher: MinusculeMatcher) : MinusculeMatcher() {
  override val pattern: String
    get() = myDelegateMatcher.pattern

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? {
    return myDelegateMatcher.matchingFragments(name)
  }

  override fun match(name: String): List<TextRange>? {
    return myDelegateMatcher.match(name)
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<TextRange>?): Int {
    val degree = myDelegateMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    return when {
      fragments.isNullOrEmpty() -> degree
      fragments.first().startOffset == 0 -> degree + START_MATCH_WEIGHT
      else -> degree
    }
  }

  @Deprecated("use matchingDegree(String, Boolean, List<TextRange>)", replaceWith = ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments.toList())"))
  override fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean,
    fragments: FList<out TextRange>?,
  ): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)
  }

  private companion object {
    private const val START_MATCH_WEIGHT: Int = 10000
  }
}
