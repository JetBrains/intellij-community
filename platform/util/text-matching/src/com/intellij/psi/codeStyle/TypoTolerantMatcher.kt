// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.FList
import com.intellij.util.text.NameUtilCore
import com.intellij.util.text.matching.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.min
import kotlin.math.pow

@ApiStatus.Internal
class TypoTolerantMatcher @VisibleForTesting constructor(
  pattern: String,
  private val myMatchingMode: MatchingMode,
  hardSeparators: String,
) : MinusculeMatcher() {
  private val myPattern: CharArray
  private val myMixedCase: Boolean
  private val myHasSeparators: Boolean
  private val myHasDots: Boolean
  private val isLowerCase: BooleanArray
  private val isUpperCase: BooleanArray
  private val isWordSeparator: BooleanArray
  private val toUpperCase: CharArray
  private val toLowerCase: CharArray
  private val myMeaningfulCharacters: CharArray
  private val myMinNameLength: Int
  private val myHardSeparators = hardSeparators.toCharArray()

  /**
   * Constructs a matcher by a given pattern.
   * @param pattern the pattern
   * @param myMatchingMode matching mode
   * @param myHardSeparators A string of characters (empty by default). Lowercase humps don't work for parts separated by any of these characters.
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  init {
    myPattern = pattern.removeSuffix("* ").toCharArray()
    isLowerCase = BooleanArray(myPattern.size)
    isUpperCase = BooleanArray(myPattern.size)
    isWordSeparator = BooleanArray(myPattern.size)
    toUpperCase = CharArray(myPattern.size)
    toLowerCase = CharArray(myPattern.size)
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

  override val pattern: String
    get() = myPattern.concatToString()

  private fun isWordSeparator(c: Char): Boolean {
    return c.isWhitespace() || c == '_' || c == '-' || c == ':' || c == '+' || c == '.'
  }

  private fun nextWord(name: String, start: Int, isAsciiName: Boolean): Int {
    return when {
      start < name.length && name[start].isDigit() -> start + 1 //treat each digit as a separate hump
      isAsciiName -> AsciiUtils.nextWordAscii(name, start)
      else -> NameUtilCore.nextWord(name, start)
    }
  }

  private fun appendRange(ranges: List<MatchedFragment>, range: MatchedFragment): List<MatchedFragment> {
    if (ranges.isEmpty()) {
      return mutableListOf(range)
    }

    require(ranges is MutableList<MatchedFragment>)
    val last = ranges.last()
    if (last.startOffset == range.endOffset) {
      ranges[ranges.size - 1] = MatchedFragment(range.startOffset, last.endOffset, range.errorCount + last.errorCount)
    }
    else {
      ranges.add(range)
    }
    return ranges
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<MatchedFragment>?): Int {
    if (fragments == null) return Int.MIN_VALUE
    if (fragments.isEmpty()) return 0

    val first = fragments.first()
    val startMatch = first.startOffset == 0
    val valuedStartMatch = startMatch && valueStartCaseMatch

    var errors = 0
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
          nextHumpStart = nextWord(name, nextHumpStart, isAsciiName = false)
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

      errors += (2000.0 * (1.0 * range.errorCount / range.length).pow(2)).toInt()
    }

    val startIndex = first.startOffset
    val afterSeparator = indexOfAny(name, myHardSeparators, 0, startIndex) >= 0
    val wordStart = startIndex == 0 || NameUtilCore.isWordStart(name, startIndex) && !NameUtilCore.isWordStart(name, startIndex - 1)
    val finalMatch = fragments.last().endOffset == name.length

    return (if (wordStart) 1000 else 0) +
           matchingCase -
           fragments.size +
           -skippedHumps * 10 -
           errors +
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
      afterGap && isHumpStart && isLowerCase[patternIndex] -> {
        -10 // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
      }
      nameChar == myPattern[patternIndex] -> {
        when {
          isUpperCase[patternIndex] -> 50 // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift
          nameIndex == 0 && valuedStartMatch -> 150 // the very first letter case distinguishes classes in Java etc
          isHumpStart -> 1 // if a lowercase matches lowercase hump start, that also means something
          else -> 0
        }
      }
      isHumpStart -> {
        // disfavor hump starts where pattern letter case doesn't match name case
        -1
      }
      isLowerCase[patternIndex] && humpStartMatchedUpperCase -> {
        // disfavor lowercase non-humps matching uppercase in the name
        -1
      }
      else -> {
        0
      }
    }
  }

  override fun match(name: String): List<MatchedFragment>? {
    return if (name.length >= myMinNameLength) {
      val ascii = AsciiUtils.isAscii(name)
      Session(name, myTypoAware = false, ascii).matchingFragments()
      ?: Session(name, myTypoAware = true, ascii).matchingFragments()
    }
    else {
      null
    }
  }

  @Deprecated("use match(String)", replaceWith = ReplaceWith("match(name)"))
  override fun matchingFragments(name: String): FList<TextRange>? {
    return match(name)?.deprecated()
  }

  private inner class Session(
    private val myName: String,
    private val myTypoAware: Boolean,
    private val isAsciiName: Boolean,
  ) {
    private val myAllowTypos: Boolean = myTypoAware && isAsciiName

    private fun charAt(i: Int, errorState: ErrorState): Char {
      return if (errorState.affects(i)) errorState.getChar(myPattern, i) else myPattern[i]
    }

    private fun isWildcard(patternIndex: Int): Boolean {
      return patternIndex in myPattern.indices && isWildcard(myPattern[patternIndex])
    }

    private fun equalsIgnoreCase(patternIndex: Int, errorState: ErrorState, nameChar: Char): Boolean {
      if (errorState.affects(patternIndex)) {
        val patternChar = errorState.getChar(myPattern, patternIndex)
        return AsciiUtils.toLowerAscii(patternChar) == nameChar || AsciiUtils.toUpperAscii(patternChar) == nameChar
      }
      return toLowerCase[patternIndex] == nameChar || toUpperCase[patternIndex] == nameChar
    }

    fun isLowerCase(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) AsciiUtils.isLowerAscii(errorState.getChar(myPattern, i)) else isLowerCase[i]
    }

    fun isUpperCase(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) AsciiUtils.isUpperAscii(errorState.getChar(myPattern, i)) else isUpperCase[i]
    }

    fun isDigit(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) errorState.getChar(myPattern, i).isDigit() else myPattern[i].isDigit()
    }

    fun isWordSeparator(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) isWordSeparator(errorState.getChar(myPattern, i)) else isWordSeparator[i]
    }

    fun patternLength(errorState: ErrorState): Int {
      return errorState.length(myPattern)
    }

    fun matchingFragments(): List<MatchedFragment>? {
      val length = myName.length
      if (length < myMinNameLength) {
        return null
      }

      //we're in typo mode, but non-ascii symbols are used. so aborting
      if (myTypoAware && !isAsciiName) return null

      if (!myTypoAware) {
        var patternIndex = 0
        if (myMeaningfulCharacters.isNotEmpty()) {
          for (c in myName) {
            if (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1]) {
              patternIndex += 2
              if (patternIndex >= myMeaningfulCharacters.size) {
                break
              }
            }
          }
        }
        if (patternIndex < myMinNameLength * 2) {
          return null
        }
      }

      return matchWildcards(patternIndex = 0, nameIndex = 0, errorState = ErrorState())?.asReversed()
    }

    /**
     * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
     * and try to [.matchFragment] for it.
     */
    fun matchWildcards(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      var patternIndex = patternIndex
      if (nameIndex < 0) {
        return null
      }
      if (!isWildcard(patternIndex)) {
        return if (patternIndex == patternLength(errorState)) {
          emptyList()
        }
        else {
          matchFragment(patternIndex, nameIndex, errorState)
        }
      }

      do {
        patternIndex++
      }
      while (isWildcard(patternIndex))

      if (patternIndex == patternLength(errorState)) {
        // the trailing space should match if the pattern ends with the last word part or only its first hump character
        return if (isTrailingSpacePattern(errorState) &&
                   nameIndex != myName.length &&
                   (patternIndex < 2 || !isUpperCaseOrDigit(patternIndex - 2, errorState))) {
          val spaceIndex = myName.indexOf(' ', startIndex = nameIndex)
          if (spaceIndex >= 0) {
            mutableListOf(MatchedFragment(spaceIndex, spaceIndex + 1, errorCount = 0))
          }
          else {
            null
          }
        }
        else {
          emptyList()
        }
      }

      return matchFragment(patternIndex, nameIndex, errorState)
             ?: matchSkippingWords(patternIndex, nameIndex, true, errorState)
    }

    private fun isTrailingSpacePattern(errorState: ErrorState): Boolean {
      return isPatternChar(patternLength(errorState) - 1, ' ', errorState)
    }

    private fun isUpperCaseOrDigit(patternIndex: Int, errorState: ErrorState): Boolean {
      return isUpperCase(patternIndex, errorState) || isDigit(patternIndex, errorState)
    }

    /**
     * Enumerates places in name that could be matched by the pattern at patternIndex position
     * and invokes [.matchFragment] at those candidate positions
     */
    private fun matchSkippingWords(
      patternIndex: Int,
      nameIndex: Int,
      allowSpecialChars: Boolean,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      val wordStartsOnly = !isPatternChar(patternIndex - 1, '*', errorState) && !isWordSeparator(patternIndex, errorState)

      var nameIndex = nameIndex
      var maxFoundLength = 0
      while (true) {
        nameIndex = findNextPatternCharOccurrence(nameIndex, patternIndex, allowSpecialChars, wordStartsOnly, errorState)
        if (nameIndex < 0) {
          return null
        }
        val fragment = if (seemsLikeFragmentStart(patternIndex, nameIndex, errorState)) maxMatchingFragment(patternIndex, nameIndex, errorState) else null
        if (fragment == null) continue

        // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
        // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
        // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
        val fragmentLength = fragment.length
        if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == myName.length && isTrailingSpacePattern(errorState)) {
          if (!isMiddleMatch(patternIndex, nameIndex, errorState)) {
            maxFoundLength = fragmentLength
          }
          val ranges = matchInsideFragment(patternIndex, nameIndex, fragment)
          if (ranges != null) {
            return ranges
          }
        }
      }
    }

    private fun findNextPatternCharOccurrence(
      startAt: Int,
      patternIndex: Int,
      allowSpecialChars: Boolean,
      wordStartsOnly: Boolean,
      errorState: ErrorState,
    ): Int {
      val next = if (wordStartsOnly) {
        indexOfWordStart(patternIndex, startAt, errorState)
      }
      else {
        indexOfIgnoreCase(startAt + 1, patternIndex, errorState)
      }

      return when {
        // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
        !allowSpecialChars && !myHasSeparators && !myMixedCase && indexOfAny(myName, myHardSeparators, start = startAt, end = next) != -1 -> -1
        // if the user has typed a dot, don't skip other dots between humps
        // but one pattern dot may match several name dots
        !allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.', errorState) && indexOf(myName, '.', start = startAt, end = next, ignoreCase = false) != -1 -> -1
        else -> next
      }
    }

    private fun seemsLikeFragmentStart(patternIndex: Int, nextOccurrence: Int, errorState: ErrorState): Boolean {
      // uppercase should match either uppercase or a word start
      return !isUpperCase(patternIndex, errorState) ||
             myName[nextOccurrence].isUpperCase() ||
             NameUtilCore.isWordStart(myName, nextOccurrence) ||  // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
             !myMixedCase && myMatchingMode != MatchingMode.MATCH_CASE
    }

    private fun charEquals(patternIndex: Int, nameIndex: Int, isIgnoreCase: Boolean, allowTypos: Boolean, errorState: ErrorState): Boolean {
      val patternChar = charAt(patternIndex, errorState)
      val nameChar = myName[nameIndex]
      if (patternChar == nameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nameChar)) {
        return true
      }

      if (!myAllowTypos || !allowTypos) return false

      if (errorState.countErrors(0, patternIndex) > 0) return false
      val prevError = errorState.getError(patternIndex - 1)
      if (prevError == Error.SwapError) {
        return false
      }

      val leftMiss = leftMiss(patternChar)
      if (leftMiss != null) {
        if (leftMiss == nameChar ||
            isIgnoreCase && (AsciiUtils.toLowerAscii(leftMiss) == nameChar || AsciiUtils.toUpperAscii(leftMiss) == nameChar)) {
          errorState.addError(patternIndex, Error.TypoError(leftMiss))
          return true
        }
      }

      val rightMiss = rightMiss(patternChar)
      if (rightMiss != null) {
        if (rightMiss == nameChar ||
            isIgnoreCase && (AsciiUtils.toLowerAscii(rightMiss) == nameChar || AsciiUtils.toUpperAscii(rightMiss) == nameChar)) {
          errorState.addError(patternIndex, Error.TypoError(rightMiss))
          return true
        }
      }

      if (patternLength(errorState) > patternIndex + 1 && myName.length > nameIndex + 1) {
        val nextNameChar = myName[nameIndex + 1]
        val nextPatternChar = charAt(patternIndex + 1, errorState)

        if ((patternChar == nextNameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nextNameChar)) &&
            (nextPatternChar == nameChar || isIgnoreCase && equalsIgnoreCase(patternIndex + 1, errorState, nameChar))) {
          errorState.addError(patternIndex, Error.SwapError)
          return true
        }
      }

      if (myName.length > nameIndex + 1) {
        val nextNameChar = myName[nameIndex + 1]
        if (patternChar == nextNameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nextNameChar)) {
          errorState.addError(patternIndex, Error.MissError(nameChar))
          return true
        }
      }

      return false
    }

    private fun matchFragment(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      val fragment = maxMatchingFragment(patternIndex, nameIndex, errorState)
      return fragment?.let { matchInsideFragment(patternIndex, nameIndex, it) }
    }

    private fun maxMatchingFragment(patternIndex: Int, nameIndex: Int, baseErrorState: ErrorState): Fragment? {
      val errorState = baseErrorState.deriveFrom(patternIndex)

      if (!isFirstCharMatching(nameIndex, patternIndex, errorState)) {
        return null
      }

      val ignoreCase = myMatchingMode != MatchingMode.MATCH_CASE
      var i = 1
      while (nameIndex + i < myName.length && patternIndex + i < patternLength(errorState)) {
        if (!charEquals(patternIndex + i, nameIndex + i, ignoreCase, true, errorState)) {
          if (isDigit(patternIndex + i, errorState) && isDigit(patternIndex + i - 1, errorState)) {
            return null
          }
          break
        }
        i++
      }
      return Fragment(i, errorState)
    }

    // we've found the longest fragment matching pattern and name
    private fun matchInsideFragment(
      patternIndex: Int,
      nameIndex: Int,
      fragment: Fragment,
    ): List<MatchedFragment>? {
      // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
      val minFragment = if (isMiddleMatch(patternIndex, nameIndex, fragment.errorState)) 3 else 1
      return improveCamelHumps(patternIndex, nameIndex, fragment.length, minFragment, fragment.errorState)
             ?: findLongestMatchingPrefix(patternIndex, nameIndex, fragment.length, minFragment, fragment.errorState)
    }

    private fun isMiddleMatch(patternIndex: Int, nameIndex: Int, errorState: ErrorState): Boolean {
      return isPatternChar(patternIndex - 1, '*', errorState) && !isWildcard(patternIndex + 1) &&
             myName[nameIndex].isLetterOrDigit() && !NameUtilCore.isWordStart(myName, nameIndex)
    }

    fun findLongestMatchingPrefix(
      patternIndex: Int,
      nameIndex: Int,
      fragmentLength: Int, minFragment: Int,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      if (patternIndex + fragmentLength >= patternLength(errorState)) {
        val errors = errorState.countErrors(patternIndex, patternIndex + fragmentLength)
        return if (errors == fragmentLength) {
          null
        }
        else {
          mutableListOf(MatchedFragment(nameIndex, nameIndex + fragmentLength, errors))
        }
      }

      // try to match the remainder of pattern with the remainder of name
      // it may not succeed with the longest matching fragment, then try shorter matches
      var i = fragmentLength
      while (i >= minFragment || isWildcard(patternIndex + i)) {
        val derivedErrorState = errorState.deriveFrom(patternIndex + i)
        val ranges = if (isWildcard(patternIndex + i)) {
          matchWildcards(patternIndex + i, nameIndex + i, derivedErrorState)
        }
        else {
          matchSkippingWords(patternIndex + i, nameIndex + i, allowSpecialChars = false, derivedErrorState)
        }
        if (ranges != null) {
          val errors = errorState.countErrors(patternIndex, patternIndex + i)
          return if (errors == i) {
            null
          }
          else {
            appendRange(ranges, MatchedFragment(nameIndex, nameIndex + i, errors))
          }
        }
        i--
      }
      return null
    }

    /**
     * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
     * but we try to find uppercase "U" later in name for better matching degree
     */
    fun improveCamelHumps(
      patternIndex: Int,
      nameIndex: Int,
      maxFragment: Int,
      minFragment: Int,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      for (i in minFragment..<maxFragment) {
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i, errorState)) {
          val ranges = findUppercaseMatchFurther(patternIndex + i, nameIndex + i, errorState.deriveFrom(patternIndex + i))
          if (ranges != null) {
            val errors = errorState.countErrors(patternIndex, patternIndex + i)
            return if (errors == i) {
              null
            }
            else {
              appendRange(ranges, MatchedFragment(nameIndex, nameIndex + i, errors))
            }
          }
        }
      }
      return null
    }

    fun isUppercasePatternVsLowercaseNameChar(patternIndex: Int, nameIndex: Int, errorState: ErrorState): Boolean {
      return isUpperCase(patternIndex, errorState) && !charEquals(patternIndex, nameIndex, false, false, errorState)
    }

    fun findUppercaseMatchFurther(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): List<MatchedFragment>? {
      val nextWordStart = indexOfWordStart(patternIndex, nameIndex, errorState)
      return matchWildcards(patternIndex, nextWordStart, errorState.deriveFrom(patternIndex))
    }

    fun isFirstCharMatching(nameIndex: Int, patternIndex: Int, errorState: ErrorState): Boolean {
      if (nameIndex >= myName.length) return false

      val ignoreCase = myMatchingMode != MatchingMode.MATCH_CASE
      if (!charEquals(patternIndex, nameIndex, ignoreCase, true, errorState)) return false

      return !(myMatchingMode == MatchingMode.FIRST_LETTER &&
               (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
               hasCase(patternIndex, errorState) && isUpperCase(patternIndex, errorState) != myName[0].isUpperCase())
    }

    private fun hasCase(patternIndex: Int, errorState: ErrorState): Boolean {
      return isUpperCase(patternIndex, errorState) || isLowerCase(patternIndex, errorState)
    }

    fun isPatternChar(patternIndex: Int, c: Char, errorState: ErrorState): Boolean {
      return patternIndex >= 0 && patternIndex < patternLength(errorState) && charAt(patternIndex, errorState) == c
    }

    fun indexOfWordStart(patternIndex: Int, startFrom: Int, errorState: ErrorState): Int {
      if (startFrom >= myName.length ||
          myMixedCase && isLowerCase(patternIndex, errorState) && !(patternIndex > 0 && isWordSeparator(patternIndex - 1, errorState))) {
        return -1
      }
      var nextWordStart = startFrom
      while (true) {
        nextWordStart = nextWord(myName, nextWordStart, isAsciiName)
        if (nextWordStart >= myName.length) {
          return -1
        }
        if (charEquals(patternIndex, nextWordStart, true, true, errorState)) {
          return nextWordStart
        }
      }
    }

    fun indexOfIgnoreCase(fromIndex: Int, patternIndex: Int, errorState: ErrorState): Int {
      val p = charAt(patternIndex, errorState)
      if (isAsciiName && AsciiUtils.isAscii(p)) {
        val i = indexIgnoringCaseAscii(fromIndex, p)
        if (i != -1) return i

        if (myAllowTypos) {
          val leftMiss = leftMiss(p)?.let { indexIgnoringCaseAscii(fromIndex, it) }.takeIf { it != -1 }
          if (leftMiss != null) return leftMiss

          val rightMiss = rightMiss(p)?.let { indexIgnoringCaseAscii(fromIndex, it) }.takeIf { it != -1 }
          if (rightMiss != null) return rightMiss
        }

        return -1
      }
      return myName.indexOf(p, startIndex = fromIndex, ignoreCase = true)
    }

    fun indexIgnoringCaseAscii(fromIndex: Int, p: Char): Int {
      val pUpper = AsciiUtils.toUpperAscii(p)
      val pLower = AsciiUtils.toLowerAscii(p)
      for (i in fromIndex..<myName.length) {
        val c = myName[i]
        if (c == p || AsciiUtils.toUpperAscii(c) == pUpper || AsciiUtils.toLowerAscii(c) == pLower) {
          return i
        }
      }
      return -1
    }
  }

  private fun isWildcard(ch: Char): Boolean = ch == ' ' || ch == '*'

  @NonNls
  override fun toString(): @NonNls String {
    return "TypoTolerantMatcher{myPattern=${pattern}, myMatchingMode=$myMatchingMode}"
  }

  private data class ErrorWithIndex(val index: Int, val error: Error)

  private class ErrorState(private val myBase: ErrorState? = null, private val myDeriveIndex: Int = 0) {
    private var myAffected: BitSet? = null
    private var myAllAffectedAfter = Int.MAX_VALUE
    private var myErrors: MutableList<ErrorWithIndex>? = null
    private var myPattern: CharArray? = null

    fun deriveFrom(index: Int): ErrorState {
      return ErrorState(this, index)
    }

    fun addError(index: Int, error: Error) {
      val errors = myErrors ?: mutableListOf<ErrorWithIndex>().also { myErrors = it }
      val errorWithIndex = ErrorWithIndex(index, error)
      errors.add(errorWithIndex)
      updateAffected(index, error)
      myPattern?.let {
        myPattern = applyError(it, errorWithIndex)
      }
    }

    private fun updateAffected(index: Int, error: Error) {
      val affected = myAffected ?: BitSet().also { myAffected = it }
      affected.set(index)
      when (error) {
        is Error.SwapError -> {
          affected.set(index + 1)
        }
        is Error.MissError -> {
          myAllAffectedAfter = min(index, myAllAffectedAfter)
        }
        is Error.TypoError -> {}
      }
    }

    fun countErrors(start: Int, end: Int): Int {
      var errors = 0
      if (myBase != null && start < myDeriveIndex) {
        errors += myBase.countErrors(start, myDeriveIndex)
      }

      if (myErrors != null) {
        for (error in myErrors) {
          if (error.index in start..<end) {
            errors++
          }
        }
      }

      return errors
    }

    fun getChar(pattern: CharArray, index: Int): Char {
      val pattern = myPattern ?: applyErrors(pattern.copyOf(), Int.MAX_VALUE).also { myPattern = it }
      return pattern[index]
    }

    fun applyErrors(pattern: CharArray, upToIndex: Int): CharArray {
      var result = pattern
      if (myBase != null) {
        result = myBase.applyErrors(pattern, min(myDeriveIndex, upToIndex))
      }
      if (myErrors != null) {
        for (error in myErrors) {
          if (error.index < upToIndex) {
            result = applyError(result, error)
          }
        }
      }
      return result
    }

    private fun applyError(pattern: CharArray, error: ErrorWithIndex): CharArray {
      return when (val e = error.error) {
        is Error.TypoError -> {
          pattern[error.index] = e.correctChar
          pattern
        }
        is Error.SwapError -> {
          val index = error.index
          val c = pattern[index]
          pattern[index] = pattern[index + 1]
          pattern[index + 1] = c
          pattern
        }
        is Error.MissError -> {
          pattern.insert(error.index, e.missedChar)
        }
      }
    }

    private fun CharArray.insert(index: Int, element: Char): CharArray {
      return if (size == index) {
        this + element
      }
      else {
        CharArray(size + 1).also { destination ->
          copyInto(destination, destinationOffset = 0, startIndex = 0, endIndex = index)
          destination[index] = element
          copyInto(destination, destinationOffset = index + 1, startIndex = index)
        }
      }
    }

    fun affects(index: Int): Boolean {
      return localAffects(index) || (myBase != null && myBase.affects(index))
    }

    fun localAffects(index: Int): Boolean {
      return index >= myAllAffectedAfter || myAffected != null && myAffected!!.get(index)
    }

    fun getError(i: Int): Error? {
      if (myErrors != null && myAffected!!.get(i)) {
        for (error in myErrors) {
          if (error.index == i) return error.error
        }
      }

      if (myBase != null && myDeriveIndex > i) {
        return myBase.getError(i)
      }

      return null
    }

    fun numMisses(end: Int): Int {
      var numMisses = 0
      if (myErrors != null && end > 0) {
        for (error in myErrors) {
          if (error.index < end && error.error is Error.MissError) {
            numMisses++
          }
        }
      }
      return numMisses + (myBase?.numMisses(myDeriveIndex) ?: 0)
    }

    fun length(pattern: CharArray): Int {
      if (myPattern != null) {
        return myPattern!!.size
      }
      return pattern.size + numMisses(Int.MAX_VALUE)
    }
  }

  private sealed interface Error {
    data class TypoError(val correctChar: Char) : Error
    object SwapError : Error
    data class MissError(val missedChar: Char) : Error
  }

  private class Fragment(val length: Int, val errorState: ErrorState)

  private val keyboard = arrayOf(charArrayOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                                 charArrayOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
                                 charArrayOf('z', 'x', 'c', 'v', 'b', 'n', 'm'))

  private fun leftMiss(aChar: Char): Char? {
    val isUpperCase = AsciiUtils.isUpperAscii(aChar)
    val lc = if (isUpperCase) AsciiUtils.toLowerAscii(aChar) else aChar

    for (line in keyboard) {
      for (j in line.indices) {
        val c = line[j]
        if (c == lc) {
          return if (j > 0) {
            if (isUpperCase) AsciiUtils.toUpperAscii(line[j - 1]) else line[j - 1]
          }
          else {
            null
          }
        }
      }
    }
    return null
  }

  private fun rightMiss(aChar: Char): Char? {
    val isUpperCase = AsciiUtils.isUpperAscii(aChar)
    val lc = if (isUpperCase) AsciiUtils.toLowerAscii(aChar) else aChar

    for (line in keyboard) {
      for (j in line.indices) {
        val c = line[j]
        if (c == lc) {
          return if (j + 1 < line.size) {
            if (isUpperCase) AsciiUtils.toUpperAscii(line[j + 1]) else line[j + 1]
          }
          else {
            null
          }
        }
      }
    }
    return null
  }
}