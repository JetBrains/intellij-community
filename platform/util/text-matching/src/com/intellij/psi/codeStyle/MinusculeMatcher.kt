// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import com.intellij.util.text.Matcher
import kotlin.jvm.JvmStatic

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search, etc.
 * 
 * 
 * Inheritors MUST override the [matchingFragments] and [matchingDegree] methods,
 * they are not abstract for binary compatibility.
 * 
 * @see NameUtil.buildMatcher
 */
abstract class MinusculeMatcher protected constructor() : Matcher {
  abstract val pattern: String

  override fun matches(name: String): Boolean {
    return match(name) != null
  }

  open fun match(name: String): List<TextRange>? {
    return matchingFragments(name) as List<TextRange>?
  }

  @Deprecated("use match(String)", ReplaceWith("match(name)"))
  open fun matchingFragments(name: String): FList<TextRange>? {
    throw UnsupportedOperationException()
  }


  @Deprecated("use matchingDegree(String, Boolean, List<TextRange>)", ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments as List<TextRange>?)"))
  open fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    throw UnsupportedOperationException()
  }

  open fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<TextRange>?): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments?.asReversed()?.let(FList<TextRange>::createFromReversed))
  }

  open fun matchingDegree(name: String, valueStartCaseMatch: Boolean): Int {
    return matchingDegree(name, valueStartCaseMatch, match(name))
  }

  open fun matchingDegree(name: String): Int {
    return matchingDegree(name, false)
  }

  open fun isStartMatch(name: String): Boolean {
    val fragments = match(name)
    return fragments != null && isStartMatch(fragments)
  }

  companion object {
    @JvmStatic
    fun isStartMatch(fragments: Iterable<TextRange>): Boolean {
      val iterator = fragments.iterator()
      return !iterator.hasNext() || iterator.next().startOffset == 0
    }
  }
}
