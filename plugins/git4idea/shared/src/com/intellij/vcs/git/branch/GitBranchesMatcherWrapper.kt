// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.util.containers.FList
import org.jetbrains.annotations.ApiStatus

/**
 * Adds weight to matching degree returned by [delegate], preferring start strings starting with the pattern
 */
@ApiStatus.Internal
class GitBranchesMatcherWrapper(private val delegate: MinusculeMatcher) : MinusculeMatcher() {
  override val pattern: String
    get() = delegate.pattern

  override fun match(name: String): List<TextRange>? = delegate.match(name)

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? = delegate.matchingFragments(name)

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<TextRange>?): Int {
    val degree = delegate.matchingDegree(name, valueStartCaseMatch, fragments)
    return fragments?.firstOrNull()?.startOffset?.let { degree + MATCH_OFFSET - it } ?: degree
  }

  @Deprecated("use matchingDegree(String, Boolean, List<TextRange>)", replaceWith = ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)"))
  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)
  }

  companion object {
    private const val MATCH_OFFSET = 10000
  }
}