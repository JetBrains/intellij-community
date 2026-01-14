// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

private val log = logger<GradleDependencyCompletionMatcher>()

@ApiStatus.Internal
class GradleDependencyCompletionMatcher(prefix: String) : PrefixMatcher(prefix) {
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

  private fun tryGetMatchingFragments(prefix: String, name: String): List<MatchedFragment> {
    // handle top level completion case:
    // implementation("org.example:lib-implementation:1.0") - "implementation" in the artifact name should be matched
    val start = name.indexOf("(").coerceAtLeast(0)
    val prefixParts = prefix.split(":")
    val nameParts = name.substring(start).split(":")
    val result = mutableListOf<MatchedFragment>()
    var offset = start
    var j = 0
    for (i in 0 until prefixParts.size) {
      var matchingFragment: MatchedFragment? = null
      while (j < nameParts.size && matchingFragment == null) {
        matchingFragment = getMatchingFragment(offset, prefixParts[i], nameParts[j])
        offset += nameParts[j].length + 1
        j++
      }
      if (matchingFragment == null) {
        // an empty list means no match, null means use default matching strategy
        return emptyList()
      }
      result.add(matchingFragment)
    }
    return result
  }

  private fun getMatchingFragment(offset: Int, prefixPart: String, name: String): MatchedFragment? {
    val from = name.indexOf(prefixPart)
    if (from == -1) {
      return null
    }
    return MatchedFragment(from + offset, from + offset + prefixPart.length)
  }
}