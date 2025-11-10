// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList

class PreferStartMatchMatcherWrapper(private val myDelegateMatcher: MinusculeMatcher) : MinusculeMatcher() {
  override val pattern: String
    get() = myDelegateMatcher.pattern

  override fun matchingFragments(name: String): FList<TextRange>? {
    return myDelegateMatcher.matchingFragments(name)
  }

  override fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean,
    fragments: FList<out TextRange?>?,
  ): Int {
    val degree = myDelegateMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    return when {
      fragments.isNullOrEmpty() -> degree
      fragments.getHead().startOffset == 0 -> degree + START_MATCH_WEIGHT
      else -> degree
    }
  }

  private companion object {
    private const val START_MATCH_WEIGHT: Int = 10000
  }
}
