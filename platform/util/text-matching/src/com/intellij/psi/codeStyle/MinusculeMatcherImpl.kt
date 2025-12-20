// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.Strings
import com.intellij.util.containers.FList
import com.intellij.util.text.CharArrayCharSequence
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.NameUtilCore
import com.intellij.util.text.NameUtilCore.isWordStart
import com.intellij.util.text.matching.MatchingMode
import org.jetbrains.annotations.NonNls

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 * 
 * @see NameUtil.buildMatcher
 */
internal class MinusculeMatcherImpl(pattern: String, private val myMatchingMode: MatchingMode, private val myHardSeparators: String) :
  MinusculeMatcher() {
  private val myPattern: CharArray
  private val myHasHumps: Boolean
  private val myHasSeparators: Boolean
  private val myHasDots: Boolean
  private val isLowerCase: BooleanArray
  private val isUpperCase: BooleanArray
  private val isWordSeparator: BooleanArray
  private val toUpperCase: CharArray
  private val toLowerCase: CharArray
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
    myPattern = Strings.trimEnd(pattern, "* ").toCharArray()
    isLowerCase = BooleanArray(myPattern.size)
    isUpperCase = BooleanArray(myPattern.size)
    isWordSeparator = BooleanArray(myPattern.size)
    toUpperCase = CharArray(myPattern.size)
    toLowerCase = CharArray(myPattern.size)
    val meaningful = StringBuilder()
    for (k in myPattern.indices) {
      val c = myPattern[k]
      isLowerCase[k] = Character.isLowerCase(c)
      isUpperCase[k] = Character.isUpperCase(c)
      isWordSeparator[k] = isWordSeparator(c)
      toUpperCase[k] = Strings.toUpperCase(c)
      toLowerCase[k] = Strings.toLowerCase(c)
      if (!isWildcard(k)) {
        meaningful.append(toLowerCase[k])
        meaningful.append(toUpperCase[k])
      }
    }
    var i = 0
    while (isWildcard(i)) i++
    myHasHumps = hasFlag(i + 1, isUpperCase) && hasFlag(i, isLowerCase)
    myHasSeparators = hasFlag(i, isWordSeparator)
    myHasDots = hasDots(i)
    myMeaningfulCharacters = meaningful.toString().toCharArray()
    myMinNameLength = myMeaningfulCharacters.size / 2
  }

  private fun hasFlag(start: Int, flags: BooleanArray): Boolean {
    for (i in start..<myPattern.size) {
      if (flags[i]) {
        return true
      }
    }
    return false
  }

  private fun hasDots(start: Int): Boolean {
    for (i in start..<myPattern.size) {
      if (myPattern[i] == '.') {
        return true
      }
    }
    return false
  }

  public override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: FList<out TextRange>?): Int {
    if (fragments == null) return Int.Companion.MIN_VALUE
    if (fragments.isEmpty()) return 0

    val first: TextRange = fragments.getHead()
    val startMatch = first.getStartOffset() == 0
    val valuedStartMatch = startMatch && valueStartCaseMatch

    var matchingCase = 0
    var p = -1

    var skippedHumps = 0
    var nextHumpStart = 0
    var humpStartMatchedUpperCase = false
    for (range in fragments) {
      for (i in range.getStartOffset()..<range.getEndOffset()) {
        val afterGap = i == range.getStartOffset() && first !== range
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

        val c = name.get(i)
        p = Strings.indexOf(myPattern, c, p + 1, myPattern.size, false)
        if (p < 0) {
          break
        }

        if (isHumpStart) {
          humpStartMatchedUpperCase = c == myPattern[p] && isUpperCase[p]
        }

        matchingCase += evaluateCaseMatching(valuedStartMatch, p, humpStartMatchedUpperCase, i, afterGap, isHumpStart, c)
      }
    }

    val startIndex = first.getStartOffset()
    val afterSeparator = Strings.indexOfAny(name, myHardSeparators, 0, startIndex) >= 0
    val wordStart = startIndex == 0 || isWordStart(name, startIndex) && !isWordStart(name, startIndex - 1)
    val finalMatch = fragments.get(fragments.size - 1).getEndOffset() == name.length

    return (if (wordStart) 1000 else 0) +
           matchingCase -
           fragments.size + -skippedHumps * 10 +
           (if (afterSeparator) 0 else 2) +
           (if (startMatch) 1 else 0) +
           (if (finalMatch) 1 else 0)
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
    if (afterGap && isHumpStart && isLowerCase[patternIndex]) {
      return -10 // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
    }
    if (nameChar == myPattern[patternIndex]) {
      if (isUpperCase[patternIndex]) return 50 // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift

      if (nameIndex == 0 && valuedStartMatch) return 150 // the very first letter case distinguishes classes in Java etc

      if (isHumpStart) return 1 // if a lowercase matches lowercase hump start, that also means something
    }
    else if (isHumpStart) {
      // disfavor hump starts where pattern letter case doesn't match name case
      return -1
    }
    else if (isLowerCase[patternIndex] && humpStartMatchedUpperCase) {
      // disfavor lowercase non-humps matching uppercase in the name
      return -1
    }
    return 0
  }

  val pattern: String
    get() = String(myPattern)

  public override fun matchingFragments(name: String): FList<TextRange?>? {
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
      val c = name.get(i)
      if (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1]) {
        patternIndex += 2
      }
      ++i
    }
    if (patternIndex < myMinNameLength * 2) {
      return null
    }
    return matchWildcards(name, 0, 0)
  }

  private fun matchBySubstring(name: String): FList<TextRange?>? {
    val infix = isPatternChar(0, '*')
    val patternWithoutWildChar: CharArray = filterWildcard(myPattern)
    if (name.length < patternWithoutWildChar.size) {
      return null
    }
    if (infix) {
      val index = Strings.indexOfIgnoreCase(name, CharArrayCharSequence(patternWithoutWildChar, 0, patternWithoutWildChar.size), 0)
      if (index >= 0) {
        return FList.singleton<TextRange?>(TextRange.from(index, patternWithoutWildChar.size - 1))
      }
      return null
    }
    if (CharArrayUtil.regionMatches(patternWithoutWildChar, 0, patternWithoutWildChar.size, name)) {
      return FList.singleton<TextRange?>(TextRange(0, patternWithoutWildChar.size))
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
  ): FList<TextRange?>? {
    var patternIndex = patternIndex
    if (nameIndex < 0) {
      return null
    }
    if (!isWildcard(patternIndex)) {
      if (patternIndex == myPattern.size) {
        return FList.emptyList<TextRange?>()
      }
      return matchFragment(name, patternIndex, nameIndex)
    }

    do {
      patternIndex++
    }
    while (isWildcard(patternIndex))

    if (patternIndex == myPattern.size) {
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      if (this.isTrailingSpacePattern && nameIndex != name.length && (patternIndex < 2 || !isUpperCaseOrDigit(patternIndex - 2))) {
        val spaceIndex = name.indexOf(' ', nameIndex)
        if (spaceIndex >= 0) {
          return FList.singleton<TextRange?>(TextRange.from(spaceIndex, 1))
        }
        return null
      }
      return FList.emptyList<TextRange?>()
    }

    return matchSkippingWords(
      name, patternIndex,
      findNextPatternCharOccurrence(name, nameIndex, patternIndex),
      true
    )
  }

  private val isTrailingSpacePattern: Boolean
    get() = isPatternChar(myPattern.size - 1, ' ')

  private fun isUpperCaseOrDigit(patternIndex: Int): Boolean {
    return isUpperCase[patternIndex] || Character.isDigit(myPattern[patternIndex])
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
  ): FList<TextRange?>? {
    var nameIndex = nameIndex
    var maxFoundLength = 0
    while (nameIndex >= 0) {
      val fragmentLength =
        if (seemsLikeFragmentStart(name, patternIndex, nameIndex)) maxMatchingFragment(name, patternIndex, nameIndex) else 0

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
    return if (!isPatternChar(patternIndex - 1, '*') && !isWordSeparator[patternIndex])
      indexOfWordStart(name, patternIndex, startAt)
    else
      indexOfIgnoreCase(name, startAt, myPattern[patternIndex], patternIndex)
  }

  private fun checkForSpecialChars(name: String, start: Int, end: Int, patternIndex: Int): Int {
    if (end < 0) return -1

    // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
    if (!myHasSeparators && !myHasHumps && Strings.containsAnyChar(name, myHardSeparators, start, end)) {
      return -1
    }
    // if the user has typed a dot, don't skip other dots between humps
    // but one pattern dot may match several name dots
    if (myHasDots && !isPatternChar(patternIndex - 1, '.') && Strings.contains(name, start, end, '.')) {
      return -1
    }
    return end
  }

  private fun seemsLikeFragmentStart(name: String, patternIndex: Int, nextOccurrence: Int): Boolean {
    // uppercase should match either uppercase or a word start
    return !isUpperCase[patternIndex] ||
           Character.isUpperCase(name.get(nextOccurrence)) ||
           isWordStart(
             name,
             nextOccurrence
           ) ||  // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
           !myHasHumps && myMatchingMode != MatchingMode.MATCH_CASE
  }

  private fun charEquals(patternChar: Char, patternIndex: Int, c: Char, isIgnoreCase: Boolean): Boolean {
    return patternChar == c ||
           isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c)
  }

  private fun matchFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
  ): FList<TextRange?>? {
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
      val nameChar = name.get(nameIndex + i)
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
    return Character.isDigit(myPattern[patternIndex]) && Character.isDigit(myPattern[patternIndex - 1]) && Character.isDigit(nameChar)
  }

  // we've found the longest fragment matching pattern and name
  private fun matchInsideFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    fragmentLength: Int,
  ): FList<TextRange?>? {
    // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
    val minFragment = if (isMiddleMatch(name, patternIndex, nameIndex))
      3
    else
      1

    val camelHumpRanges = improveCamelHumps(name, patternIndex, nameIndex, fragmentLength, minFragment)
    if (camelHumpRanges != null) {
      return camelHumpRanges
    }

    return findLongestMatchingPrefix(name, patternIndex, nameIndex, fragmentLength, minFragment)
  }

  private fun isMiddleMatch(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
           Character.isLetterOrDigit(name.get(nameIndex)) && !isWordStart(name, nameIndex)
  }

  private fun findLongestMatchingPrefix(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    fragmentLength: Int, minFragment: Int,
  ): FList<TextRange?>? {
    if (patternIndex + fragmentLength >= myPattern.size) {
      return FList.singleton<TextRange?>(TextRange.from(nameIndex, fragmentLength))
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    var i = fragmentLength
    while (i >= minFragment || (i > 0 && isWildcard(patternIndex + i))) {
      val ranges: FList<TextRange?>?
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
        return prependRange(ranges, nameIndex, i)
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
  ): FList<TextRange?>? {
    for (i in minFragment..<maxFragment) {
      if (isUppercasePatternVsLowercaseNameChar(name, patternIndex + i, nameIndex + i)) {
        val ranges = findUppercaseMatchFurther(name, patternIndex + i, nameIndex + i)
        if (ranges != null) {
          return prependRange(ranges, nameIndex, i)
        }
      }
    }
    return null
  }

  private fun isUppercasePatternVsLowercaseNameChar(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isUpperCase[patternIndex] && myPattern[patternIndex] != name.get(nameIndex)
  }

  private fun findUppercaseMatchFurther(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
  ): FList<TextRange?>? {
    val nextWordStart = indexOfWordStart(name, patternIndex, nameIndex)
    return matchWildcards(name, patternIndex, nextWordStart)
  }

  private fun isFirstCharMatching(name: String, nameIndex: Int, patternIndex: Int): Boolean {
    if (nameIndex >= name.length) return false

    val ignoreCase = myMatchingMode != MatchingMode.MATCH_CASE
    val patternChar = myPattern[patternIndex]
    if (!charEquals(patternChar, patternIndex, name.get(nameIndex), ignoreCase)) return false

    if (myMatchingMode == MatchingMode.FIRST_LETTER &&
        (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
        hasCase(patternIndex) && isUpperCase[patternIndex] != Character.isUpperCase(name.get(0))
    ) {
      return false
    }
    return true
  }

  private fun hasCase(patternIndex: Int): Boolean {
    return isUpperCase[patternIndex] || isLowerCase[patternIndex]
  }

  private fun isWildcard(patternIndex: Int): Boolean {
    if (patternIndex >= 0 && patternIndex < myPattern.size) {
      val pc = myPattern[patternIndex]
      return pc == ' ' || pc == '*'
    }
    return false
  }

  private fun isPatternChar(patternIndex: Int, c: Char): Boolean {
    return patternIndex >= 0 && patternIndex < myPattern.size && myPattern[patternIndex] == c
  }

  private fun indexOfWordStart(name: String, patternIndex: Int, startFrom: Int): Int {
    val p = myPattern[patternIndex]
    if (startFrom >= name.length ||
        myHasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])
    ) {
      return -1
    }
    var i = startFrom
    val isSpecialSymbol = !Character.isLetterOrDigit(p)
    while (true) {
      i = indexOfIgnoreCase(name, i, p, patternIndex)
      if (i < 0) return -1

      if (isSpecialSymbol || isWordStart(name, i)) return i

      i++
    }
  }

  private fun indexOfIgnoreCase(name: String, fromIndex: Int, p: Char, patternIndex: Int): Int {
    if (Strings.isAscii(p)) {
      val pUpper = toUpperCase[patternIndex]
      val pLower = toLowerCase[patternIndex]
      for (i in fromIndex..<name.length) {
        val c = name.get(i)
        if (c == pUpper || c == pLower) {
          return i
        }
      }
      return -1
    }
    return Strings.indexOfIgnoreCase(name, p, fromIndex)
  }

  @NonNls
  override fun toString(): @NonNls String {
    return "MinusculeMatcherImpl{myPattern=" + String(myPattern) + ", myOptions=" + myMatchingMode + '}'
  }

  companion object {
    /** Camel-hump matching is >O(n), so for larger prefixes we fall back to simpler matching to avoid pauses  */
    private const val MAX_CAMEL_HUMP_MATCHING_LENGTH = 100

    private fun isWordSeparator(c: Char): Boolean {
      return Character.isWhitespace(c) || c == '_' || c == '-' || c == ':' || c == '+' || c == '.'
    }

    private fun nextWord(name: String, start: Int): Int {
      if (start < name.length && Character.isDigit(name.get(start))) {
        return start + 1 //treat each digit as a separate hump
      }
      return NameUtilCore.nextWord(name, start)
    }

    private fun prependRange(ranges: FList<TextRange?>, from: Int, length: Int): FList<TextRange?> {
      val head = ranges.getHead()
      if (head != null && head.getStartOffset() == from + length) {
        return ranges.getTail().prepend(TextRange(from, head.getEndOffset()))
      }
      return ranges.prepend(TextRange.from(from, length))
    }

    private fun filterWildcard(source: CharArray): CharArray {
      val buffer = CharArray(source.size)
      var i = 0
      for (c in source) {
        if (c != '*') buffer[i++] = c
      }

      return buffer.copyOf(i)
    }
  }
}
