// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import com.intellij.util.text.Matcher
import com.intellij.util.text.NameUtilCore
import com.intellij.util.text.NameUtilCore.isWordStart
import com.intellij.util.text.matching.MatchedFragment
import com.intellij.util.text.matching.deprecated
import com.intellij.util.text.matching.indexOf
import com.intellij.util.text.matching.indexOfAny
import com.intellij.util.text.matching.undeprecate
import org.jetbrains.annotations.ApiStatus
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

  open fun match(name: String): List<MatchedFragment>? {
    return matchingFragments(name)?.undeprecate()
  }

  @Deprecated("use match(String)", ReplaceWith("match(name)"))
  open fun matchingFragments(name: String): FList<TextRange>? {
    throw UnsupportedOperationException()
  }

  @Deprecated("use matchingDegree(String, Boolean, List<MatchedFragment>)", ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments.map { MatchedFragment(it.startOffset, it.endOffset) })"))
  open fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    throw UnsupportedOperationException()
  }

  open fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<MatchedFragment>?): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments?.deprecated())
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
    @Deprecated("use isStartMatch(List<MatchedFragment>)", ReplaceWith("isStartMatch(fragments as List<MatchedFragment>)"))
    @JvmStatic
    fun isStartMatch(fragments: Iterable<TextRange>): Boolean {
      val iterator = fragments.iterator()
      return !iterator.hasNext() || iterator.next().startOffset == 0
    }

    @JvmStatic
    fun isStartMatch(sortedFragments: List<MatchedFragment>): Boolean {
      val iterator = sortedFragments.iterator()
      return !iterator.hasNext() || iterator.next().startOffset == 0
    }

    @ApiStatus.Internal
    fun calculateHumpedMatchingScore(
      pattern: CharArray,
      name: String,
      valueStartCaseMatch: Boolean,
      fragments: List<MatchedFragment>?,
      isLowerCase: BooleanArray,
      isUpperCase: BooleanArray,
      myHardSeparators: CharArray
    ): Int {
      if (fragments == null) return Int.MIN_VALUE
      if (fragments.isEmpty()) return 0

      val first = fragments.first()
      val startMatch = first.startOffset == 0
      val valuedStartMatch = startMatch && valueStartCaseMatch

      var matchingCase = 0
      var p = -1

      var skippedHumps = 0
      var nextHumpStart = 0
      var humpStartMatchedUpperCase = false
      for (range in fragments) {
        for (i in range.startOffset..<range.endOffset) {
          val afterGap = i == range.startOffset && first !== range
          var isHumpStart = false
          while (nextHumpStart <= i) {
            if (nextHumpStart == i) {
              isHumpStart = true
            }
            else if (afterGap) {
              skippedHumps++
            }
            nextHumpStart = if (nextHumpStart < name.length && name[nextHumpStart].isDigit()) {
              nextHumpStart + 1 //treat each digit as a separate hump
            }
            else {
              NameUtilCore.nextWord(name, nextHumpStart)
            }
          }

          val c = name[i]
          p = indexOf(pattern, c, p + 1, pattern.size, ignoreCase = true)
          if (p < 0) {
            break
          }

          if (isHumpStart) {
            humpStartMatchedUpperCase = c == pattern[p] && isUpperCase[p]
          }

          matchingCase += evaluateCaseMatching(
            pattern = pattern,
            valuedStartMatch = valuedStartMatch,
            patternIndex = p,
            humpStartMatchedUpperCase = humpStartMatchedUpperCase,
            nameIndex = i,
            afterGap = afterGap,
            isHumpStart = isHumpStart,
            nameChar = c,
            isLowerCase = isLowerCase,
            isUpperCase = isUpperCase
          )
        }
      }

      val startIndex = first.startOffset
      val afterSeparator = indexOfAny(name, myHardSeparators, start = 0, end = startIndex) >= 0
      val wordStart = startIndex == 0 || isWordStart(name, startIndex) && !isWordStart(name, startIndex - 1)
      val finalMatch = fragments.last().endOffset == name.length

      return (if (wordStart) 1000 else 0) +
             matchingCase -
             fragments.size + -skippedHumps * 10 +
             (if (afterSeparator) 0 else 2) +
             (if (startMatch) 1 else 0) +
             (if (finalMatch) 1 else 0)
    }

    internal fun evaluateCaseMatching(
      pattern: CharArray,
      valuedStartMatch: Boolean,
      patternIndex: Int,
      humpStartMatchedUpperCase: Boolean,
      nameIndex: Int,
      afterGap: Boolean,
      isHumpStart: Boolean,
      nameChar: Char,
      isLowerCase: BooleanArray,
      isUpperCase: BooleanArray,
    ): Int {
      return when {
        afterGap && isHumpStart && isLowerCase[patternIndex] -> -10 // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
        nameChar == pattern[patternIndex] -> {
          when {
            isUpperCase[patternIndex] -> 50 // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift
            nameIndex == 0 && valuedStartMatch -> 150 // the very first letter case distinguishes classes in Java etc
            isHumpStart -> 1 // if lowercase matches lowercase hump start, that also means something
            else -> 0
          }
        }
        isHumpStart -> -1 // disfavor hump starts where pattern letter case doesn't match name case
        isLowerCase[patternIndex] && humpStartMatchedUpperCase -> -1 // disfavor lowercase non-humps matching uppercase in the name
        else -> {
          0
        }
      }
    }
  }
}
