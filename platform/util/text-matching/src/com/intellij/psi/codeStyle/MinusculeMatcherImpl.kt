// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import com.intellij.util.text.NameUtilCore
import com.intellij.util.text.NameUtilCore.isWordStart
import com.intellij.util.text.matching.*
import org.jetbrains.annotations.NonNls

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 * 
 * @see NameUtil.buildMatcher
 */
internal class MinusculeMatcherImpl(pattern: String, private val myMatchingMode: MatchingMode, hardSeparators: String) :
  MinusculeMatcher() {
  private val myPattern: CharArray = pattern.removeSuffix("* ").toCharArray()
  private val isLowerCase: BooleanArray = BooleanArray(myPattern.size)
  private val isUpperCase: BooleanArray = BooleanArray(myPattern.size)
  private val isWordSeparator: BooleanArray = BooleanArray(myPattern.size)
  private val toUpperCase: CharArray = CharArray(myPattern.size)
  private val toLowerCase: CharArray = CharArray(myPattern.size)
private val myHardSeparators: CharArray = hardSeparators.toCharArray()

  private val myMixedCase: Boolean
  private val myHasSeparators: Boolean
  private val myHasDots: Boolean
  private val myMeaningfulCharacters: CharArray
  private val myMinNameLength: Int

  /**
   * Constructs a matcher by a given pattern.
   * @param pattern the pattern
   * @param myMatchingMode case sensitivity settings
   * @param myHardSeparators A string of characters (empty by default). Lowercase humps don't work for parts separated by any of these characters.
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  init {
    val meaningful = mutableListOf<Char>()
    var seenNonWildcard = false
    var seenLowerCase = false
    var seenUpperCaseNotImmediatelyAfterWildcard = false
    var hasDots = false
    var hasSeparators = false
    myPattern.forEachIndexed { k, c ->
      val isWordSeparator = isWordSeparator(c)
      val isUpperCase = c.isUpperCase()
      val isLowerCase = c.isLowerCase()
      val toUpperCase = c.uppercaseChar()
      val toLowerCase = c.lowercaseChar()
      if (isLowerCase) {
        seenLowerCase = true
      }
      if (c == '.') {
        hasDots = true
      }
      if (seenNonWildcard && isUpperCase) {
        seenUpperCaseNotImmediatelyAfterWildcard = true
      }
      if (!isWildcard(c)) {
        seenNonWildcard = true
        meaningful.add(toLowerCase)
        meaningful.add(toUpperCase)
      }
      if (seenNonWildcard && isWordSeparator) {
        hasSeparators = true
      }

      this.isWordSeparator[k] = isWordSeparator
      this.isUpperCase[k] = isUpperCase
      this.isLowerCase[k] = isLowerCase
      this.toUpperCase[k] = toUpperCase
      this.toLowerCase[k] = toLowerCase
    }

    myHasDots = hasDots
    myMixedCase = seenLowerCase && seenUpperCaseNotImmediatelyAfterWildcard
    myHasSeparators = hasSeparators

    myMeaningfulCharacters = meaningful.toCharArray()
    myMinNameLength = myMeaningfulCharacters.size / 2
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<MatchedFragment>?): Int {
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
          nextHumpStart = nextWord(name, nextHumpStart)
        }

        val c = name[i]
        p = indexOf(myPattern, c, p + 1, myPattern.size, ignoreCase = true)
        if (p < 0) {
          break
        }

        if (isHumpStart) {
          humpStartMatchedUpperCase = c == myPattern[p] && isUpperCase[p]
        }

        matchingCase += evaluateCaseMatching(valuedStartMatch, p, humpStartMatchedUpperCase, i, afterGap, isHumpStart, c)
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

  @Deprecated("use matchingDegree(String, Boolean, List<MatchedFragment>)", replaceWith = ReplaceWith("matchingDegree(name, valueStartCaseMatch, fragments.map { MatchedFragment(it.startOffset, it.endOffset) })"))
  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    return matchingDegree(name, valueStartCaseMatch, fragments?.undeprecate())
  }

  private fun evaluateCaseMatching(
    valuedStartMatch: Boolean,
    patternIndex: Int,
    humpStartMatchedUpperCase: Boolean,
    nameIndex: Int,
    afterGap: Boolean,
    isHumpStart: Boolean,
    nameChar: Char,
  ): Int {
    return when {
      afterGap && isHumpStart && isLowerCase[patternIndex] -> -10 // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
      nameChar == myPattern[patternIndex] -> {
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

  override val pattern: String
    get() = myPattern.concatToString()

  override fun match(name: String): List<MatchedFragment>? {
    if (name.length < myMinNameLength) {
      return null
    }

    if (myPattern.size > MAX_CAMEL_HUMP_MATCHING_LENGTH) {
      return matchBySubstring(name)
    }

    val length = name.length
    var patternIndex = 0
    var i = 0
    while (i < length && patternIndex < myMeaningfulCharacters.size) {
      val c = name[i]
      if (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1]) {
        patternIndex += 2
      }
      ++i
    }
    if (patternIndex < myMinNameLength * 2) {
      return null
    }
    return matchWildcards(name, 0, 0)?.asReversed()
  }

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? {
    return match(name)?.deprecated()
  }

  private fun matchBySubstring(name: String): List<MatchedFragment>? {
    val infix = isPatternChar(0, '*')
    val patternWithoutWildChar = filterWildcard(myPattern)
    if (name.length < patternWithoutWildChar.length) {
      return null
    }
    if (infix) {
      val index = name.indexOf(patternWithoutWildChar, ignoreCase = true)
      if (index >= 0) {
        return listOf(MatchedFragment(index, index + patternWithoutWildChar.length - 1))
      }
      return null
    }

    if (regionMatches(patternWithoutWildChar, 0, patternWithoutWildChar.length, name)) {
      return listOf(MatchedFragment(0, patternWithoutWildChar.length))
    }
    return null
  }

  /**
   * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
   * and try to [.matchFragment] for it.
   */
  private fun matchWildcards(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
  ): List<MatchedFragment>? {
    var patternIndex = patternIndex
    if (nameIndex < 0) {
      return null
    }
    if (!isWildcard(patternIndex)) {
      return if (patternIndex == myPattern.size) {
        emptyList()
      }
      else {
        matchFragment(name, patternIndex, nameIndex)
      }
    }

    do {
      patternIndex++
    }
    while (isWildcard(patternIndex))

    if (patternIndex == myPattern.size) {
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      return if (this.isTrailingSpacePattern && nameIndex != name.length && (patternIndex < 2 || !isUpperCaseOrDigit(patternIndex - 2))) {
        val spaceIndex = name.indexOf(' ', nameIndex)
        if (spaceIndex >= 0) {
          mutableListOf(MatchedFragment(spaceIndex, spaceIndex + 1))
        }
        else {
          null
        }
      }
      else {
        emptyList()
      }
    }

    return matchSkippingWords(
      name = name,
      patternIndex = patternIndex,
      nameIndex = findNextPatternCharOccurrence(name, nameIndex, patternIndex),
      allowSpecialChars = true
    )
  }

  private val isTrailingSpacePattern: Boolean
    get() = isPatternChar(myPattern.size - 1, ' ')

  private fun isUpperCaseOrDigit(patternIndex: Int): Boolean {
    return isUpperCase[patternIndex] || myPattern[patternIndex].isDigit()
  }

  /**
   * Enumerates places in name that could be matched by the pattern at patternIndex position
   * and invokes [.matchFragment] at those candidate positions
   */
  private fun matchSkippingWords(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    allowSpecialChars: Boolean,
  ): List<MatchedFragment>? {
    var nameIndex = nameIndex
    var maxFoundLength = 0
    while (nameIndex >= 0) {
      val fragmentLength = if (seemsLikeFragmentStart(name, patternIndex, nameIndex)) {
        maxMatchingFragment(name, patternIndex, nameIndex)
      }
      else {
        0
      }

      // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
      // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
      // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
      if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == name.length && this.isTrailingSpacePattern) {
        if (!isMiddleMatch(name, patternIndex, nameIndex)) {
          maxFoundLength = fragmentLength
        }
        val ranges = matchInsideFragment(name, patternIndex, nameIndex, fragmentLength)
        if (ranges != null) {
          return ranges
        }
      }
      val next = findNextPatternCharOccurrence(name, nameIndex + 1, patternIndex)
      nameIndex = if (allowSpecialChars) next else checkForSpecialChars(name, nameIndex + 1, next, patternIndex)
    }
    return null
  }

  private fun findNextPatternCharOccurrence(
    name: String,
    startAt: Int,
    patternIndex: Int,
  ): Int {
    return if (!isPatternChar(patternIndex - 1, '*') && !isWordSeparator[patternIndex]) {
      indexOfWordStart(name, patternIndex, startAt)
    }
    else {
      indexOfIgnoreCase(name, startAt, patternIndex)
    }
  }

  private fun checkForSpecialChars(name: String, start: Int, end: Int, patternIndex: Int): Int {
    if (end < 0) return -1

    // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
    if (!myHasSeparators && !myMixedCase && indexOfAny(name, myHardSeparators, start, end) != -1) {
      return -1
    }
    // if the user has typed a dot, don't skip other dots between humps
    // but one pattern dot may match several name dots
    if (myHasDots && !isPatternChar(patternIndex - 1, '.') && indexOf(name, '.', start, end, ignoreCase = false) != -1) {
      return -1
    }
    return end
  }

  private fun seemsLikeFragmentStart(name: String, patternIndex: Int, nextOccurrence: Int): Boolean {
    // uppercase should match either uppercase or a word start
    return !isUpperCase[patternIndex] ||
           name[nextOccurrence].isUpperCase() ||
           isWordStart(
             name,
             nextOccurrence
           ) ||  // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
           !myMixedCase && myMatchingMode != MatchingMode.MATCH_CASE
  }

  private fun charEquals(patternChar: Char, patternIndex: Int, c: Char, isIgnoreCase: Boolean): Boolean {
    return patternChar == c || isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c)
  }

  private fun matchFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
  ): List<MatchedFragment>? {
    val fragmentLength = maxMatchingFragment(name, patternIndex, nameIndex)
    return if (fragmentLength == 0) null else matchInsideFragment(name, patternIndex, nameIndex, fragmentLength)
  }

  private fun maxMatchingFragment(name: String, patternIndex: Int, nameIndex: Int): Int {
    if (!isFirstCharMatching(name, nameIndex, patternIndex)) {
      return 0
    }

    var i = 1
    val ignoreCase = myMatchingMode != MatchingMode.MATCH_CASE
    while (nameIndex + i < name.length && patternIndex + i < myPattern.size) {
      val nameChar = name[nameIndex + i]
      if (!charEquals(myPattern[patternIndex + i], patternIndex + i, nameChar, ignoreCase)) {
        if (isSkippingDigitBetweenPatternDigits(patternIndex + i, nameChar)) {
          return 0
        }
        break
      }
      i++
    }
    return i
  }

  private fun isSkippingDigitBetweenPatternDigits(patternIndex: Int, nameChar: Char): Boolean {
    return myPattern[patternIndex].isDigit() && myPattern[patternIndex - 1].isDigit() && nameChar.isDigit()
  }

  // we've found the longest fragment matching pattern and name
  private fun matchInsideFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    fragmentLength: Int,
  ): List<MatchedFragment>? {
    // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
    val minFragment = if (isMiddleMatch(name, patternIndex, nameIndex)) 3 else 1
    val camelHumpRanges = improveCamelHumps(name, patternIndex, nameIndex, fragmentLength, minFragment)
    return camelHumpRanges ?: findLongestMatchingPrefix(name, patternIndex, nameIndex, fragmentLength, minFragment)
  }

  private fun isMiddleMatch(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
           name[nameIndex].isLetterOrDigit() && !isWordStart(name, nameIndex)
  }

  private fun findLongestMatchingPrefix(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    fragmentLength: Int, minFragment: Int,
  ): List<MatchedFragment>? {
    if (patternIndex + fragmentLength >= myPattern.size) {
      return mutableListOf(MatchedFragment(nameIndex, nameIndex + fragmentLength))
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    var i = fragmentLength
    while (i >= minFragment || (i > 0 && isWildcard(patternIndex + i))) {
      val ranges: List<MatchedFragment>?
      if (isWildcard(patternIndex + i)) {
        ranges = matchWildcards(name, patternIndex + i, nameIndex + i)
      }
      else {
        var nextOccurrence = findNextPatternCharOccurrence(name, nameIndex + i + 1, patternIndex + i)
        nextOccurrence = checkForSpecialChars(name, nameIndex + i, nextOccurrence, patternIndex + i)
        if (nextOccurrence >= 0) {
          ranges = matchSkippingWords(name, patternIndex + i, nextOccurrence, false)
        }
        else {
          ranges = null
        }
      }
      if (ranges != null) {
        return appendRange(ranges, nameIndex, i)
      }
      i--
    }
    return null
  }

  /**
   * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
   * but we try to find uppercase "U" later in name for better matching degree
   */
  private fun improveCamelHumps(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    maxFragment: Int,
    minFragment: Int,
  ): List<MatchedFragment>? {
    for (i in minFragment..<maxFragment) {
      if (isUppercasePatternVsLowercaseNameChar(name, patternIndex + i, nameIndex + i)) {
        val ranges = findUppercaseMatchFurther(name, patternIndex + i, nameIndex + i)
        if (ranges != null) {
          return appendRange(ranges, nameIndex, i)
        }
      }
    }
    return null
  }

  private fun isUppercasePatternVsLowercaseNameChar(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isUpperCase[patternIndex] && myPattern[patternIndex] != name[nameIndex]
  }

  private fun findUppercaseMatchFurther(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
  ): List<MatchedFragment>? {
    val nextWordStart = indexOfWordStart(name, patternIndex, nameIndex)
    return matchWildcards(name, patternIndex, nextWordStart)
  }

  private fun isFirstCharMatching(name: String, nameIndex: Int, patternIndex: Int): Boolean {
    if (nameIndex >= name.length) return false

    val ignoreCase = myMatchingMode != MatchingMode.MATCH_CASE
    val patternChar = myPattern[patternIndex]
    if (!charEquals(patternChar, patternIndex, name[nameIndex], ignoreCase)) return false

    return !(myMatchingMode == MatchingMode.FIRST_LETTER &&
             (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
             hasCase(patternIndex) && isUpperCase[patternIndex] != name[0].isUpperCase())
  }

  private fun hasCase(patternIndex: Int): Boolean {
    return isUpperCase[patternIndex] || isLowerCase[patternIndex]
  }

  private fun isWildcard(patternIndex: Int): Boolean {
    return patternIndex in myPattern.indices && isWildcard(myPattern[patternIndex])
  }

  private fun isWildcard(pc: Char): Boolean = pc == ' ' || pc == '*'

  private fun isPatternChar(patternIndex: Int, c: Char): Boolean {
    return myPattern.getOrNull(patternIndex) == c
  }

  private fun indexOfWordStart(name: String, patternIndex: Int, startFrom: Int): Int {
    val p = myPattern[patternIndex]
    if (startFrom >= name.length ||
        myMixedCase && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])) {
      return -1
    }
    var i = startFrom
    val isSpecialSymbol = !p.isLetterOrDigit()
    while (true) {
      i = indexOfIgnoreCase(name, i, patternIndex)
      if (i < 0) return -1
      if (isSpecialSymbol || isWordStart(name, i)) return i
      i++
    }
  }

  private fun indexOfIgnoreCase(name: String, fromIndex: Int, patternIndex: Int): Int {
    val p = myPattern[patternIndex]
    if (AsciiUtils.isAscii(p)) {
      val pUpper = toUpperCase[patternIndex]
      val pLower = toLowerCase[patternIndex]
      for (i in fromIndex..<name.length) {
        val c = name[i]
        if (c == pUpper || c == pLower) {
          return i
        }
      }
      return -1
    }
    return indexOf(name, p, fromIndex, name.length, ignoreCase = true)
  }

  @NonNls
  override fun toString(): @NonNls String {
    return "MinusculeMatcherImpl{myPattern=${pattern}, myMatchingMode=$myMatchingMode}"
  }

  companion object {
    /** Camel-hump matching is >O(n), so for larger prefixes we fall back to simpler matching to avoid pauses  */
    private const val MAX_CAMEL_HUMP_MATCHING_LENGTH = 100

    private fun isWordSeparator(c: Char): Boolean {
      return c.isWhitespace() || c == '_' || c == '-' || c == ':' || c == '+' || c == '.'
    }

    private fun nextWord(name: String, start: Int): Int {
      if (start < name.length && name[start].isDigit()) {
        return start + 1 //treat each digit as a separate hump
      }
      return NameUtilCore.nextWord(name, start)
    }

    private fun appendRange(ranges: List<MatchedFragment>, from: Int, length: Int): List<MatchedFragment> {
      if (ranges.isEmpty()) {
        return mutableListOf(MatchedFragment(from, from + length))
      }

      require(ranges is MutableList<MatchedFragment>)
      val last = ranges.last()
      if (last.startOffset == from + length) {
        ranges[ranges.size - 1] = MatchedFragment(from, last.endOffset)
      }
      else {
        ranges.add(MatchedFragment(from, from + length))
      }
      return ranges
    }

    private fun filterWildcard(source: CharArray): String {
      return buildString(capacity = source.size) {
        for (c in source) {
          if (c != '*') append(c)
        }
      }
    }
  }
}
