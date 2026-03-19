// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

private val log = logger<GradleDependencyCompletionMatcher>()

@ApiStatus.Internal
open class GradleDependencyCompletionMatcher(prefix: String) : PrefixMatcher(prefix) {
  override fun prefixMatches(name: String): Boolean {
    return prefix.split(":").all { name.contains(it) }
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher {
    return GradleDependencyCompletionMatcher(prefix)
  }

  override fun getMatchingFragments(prefix: String, name: String): List<MatchedFragment> {
    try {
      return tryGetMatchingFragments(prefix, name)
    }
    catch (e: Throwable) {
      log.error("Error while matching fragments, name $name, prefix $prefix", e)
      return emptyList()
    }
  }

  private fun tryGetMatchingFragments(input: String, searchResult: String): List<MatchedFragment> {
    // handle top level completion case:
    // implementation("org.example:lib-implementation:1.0") - "implementation" in the artifact name should be matched
    val start = searchResult.indexOf("(").coerceAtLeast(0)
    val prefixParts = input.split(":")
    val nameParts = searchResult.substring(start).split(":")
    val result = mutableListOf<MatchedFragment>()
    var offset = start
    var j = 0
    for (i in prefixParts.indices) {
      var matchingFragment: MatchedFragment? = null
      while (j < nameParts.size && matchingFragment == null) {
        matchingFragment = getMatchingFragment(offset, prefixParts[i], nameParts[j])
        offset += nameParts[j].length + 1
        j++
      }
      if (matchingFragment == null) {
        return tryFallbackMatching(input, searchResult, start)
      }
      result.add(matchingFragment)
    }
    return result
  }

  protected open fun tryFallbackMatching(input: String, searchResult: String, start: Int): List<MatchedFragment> {
    return emptyList()
  }

  private fun getMatchingFragment(offset: Int, prefixPart: String, name: String): MatchedFragment? {
    val from = name.indexOf(prefixPart)
    if (from == -1) {
      return null
    }
    return MatchedFragment(from + offset, from + offset + prefixPart.length)
  }
}

@ApiStatus.Internal
class GradleDependencyCompletionFuzzyMatcher(prefix: String) : GradleDependencyCompletionMatcher(prefix) {
  override fun prefixMatches(name: String): Boolean {
    return true
  }

  override fun tryFallbackMatching(input: String, searchResult: String, start: Int): List<MatchedFragment> {
    val searchable = searchResult.substring(start)

    // Try every substring of input from longest to shortest, find first match anywhere in searchResult
    for (len in input.length downTo 1) {
      for (subStart in 0..input.length - len) {
        val sub = input.substring(subStart, subStart + len)
        val idx = searchable.indexOf(sub, ignoreCase = true)
        if (idx != -1) {
          val absoluteStart = start + idx
          return listOf(MatchedFragment(absoluteStart, absoluteStart + sub.length))
        }
      }
    }

    return emptyList()
  }
}