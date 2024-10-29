// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.util.containers.FList

/**
 * Adds weight to matching degree returned by [delegate], preferring start strings starting with the pattern
 */
class GitBranchesMatcherWrapper(private val delegate: MinusculeMatcher) : MinusculeMatcher() {
  override fun getPattern(): String = delegate.pattern

  override fun matchingFragments(name: String): FList<TextRange>? = delegate.matchingFragments(name)

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    var degree = delegate.matchingDegree(name, valueStartCaseMatch, fragments)
    if (fragments.isNullOrEmpty()) return degree
    degree += MATCH_OFFSET - fragments.head.startOffset
    return degree
  }

  companion object {
    private const val MATCH_OFFSET = 10000
  }
}