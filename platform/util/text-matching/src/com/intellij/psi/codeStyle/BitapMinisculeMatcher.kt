// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.util.text.NameUtilCore
import com.intellij.util.text.matching.BitapFuzzySearchAlgorithm
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus
import kotlin.math.pow

@ApiStatus.Internal
class BitapMinisculeMatcher private constructor(
  override val pattern: String,
  private val myPatternWordMatchers: List<BitapFuzzySearchAlgorithm>,
  private val myScoringPattern: CharArray,
  private val myIsLowerCase: BooleanArray,
  private val myIsUpperCase: BooleanArray,
  private val myHardSeparators: CharArray,
) : MinusculeMatcher() {

  /**
   * Matching [name] against a list of [pattern] words (i.e., humps) and returns a list of matched fragments
   * if successful. Matching is case-insensitive.
   *
   * A single typo is allowed per whole pattern:
   * - Missing character, e.g. "foobr" instead of "foob**a**r".
   * - Extra character, e.g. "foob**b**ar" instead of "foobar".
   * - Substitution of a character, e.g. "foob**o**r" instead of "foobar".
   * - Swap of two adjacent characters, e.g. "fo**ob**ar" instead of "foobar".
   *
   * @param name The input string to be matched against each hump in the pattern.
   * @return A sorted list of matched fragments, or null if no match is found.
   */
  override fun match(name: String): List<MatchedFragment>? {
    val fragments = ArrayList<MatchedFragment>(myPatternWordMatchers.size)
    val selector = BestOccurrenceSelector(name)
    var typoSpent = false

    for (wordMatcher in myPatternWordMatchers) {
      // the budget is a single typo per pattern
      val typoAllowed = !typoSpent && wordMatcher.patternLength >= MIN_WORD_LENGTH_FOR_TYPO
      val fragment = selector.selectIn(wordMatcher, typoAllowed) ?: return null
      if (fragment.errorCount > 0) typoSpent = true
      fragments.add(fragment)
    }

    return normalize(fragments)
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean, fragments: List<MatchedFragment>?): Int {
    val score = calculateHumpedMatchingScore(myScoringPattern, name, valueStartCaseMatch, fragments,
                                             myIsLowerCase, myIsUpperCase, myHardSeparators)
    if (fragments == null) return score

    var penalty = 0
    for (fragment in fragments) {
      if (fragment.errorCount == 0) continue

      val typoDensity = if (fragment.length == 0) 1.0 else fragment.errorCount.toDouble() / fragment.length
      penalty += (ERROR_PENALTY_WEIGHT * typoDensity.pow(2)).toInt()
    }
    return score - penalty
  }

  /**
   * Sorts the fragments by offset and merges the overlapping ones and those humps that come one after another.
   */
  private fun normalize(fragments: MutableList<MatchedFragment>): List<MatchedFragment> {
    if (fragments.size == 1) return fragments
    fragments.sortBy { it.startOffset }

    val result = ArrayList<MatchedFragment>(fragments.size)
    for (fragment in fragments) {
      val last = result.lastOrNull()
      if (last != null && fragment.startOffset <= last.endOffset) {
        result[result.size - 1] = MatchedFragment(last.startOffset,
                                                  maxOf(last.endOffset, fragment.endOffset),
                                                  maxOf(last.errorCount, fragment.errorCount))
      }
      else {
        result.add(fragment)
      }
    }
    return result
  }

  /**
   * Picks occurrence of a pattern hump in [myName] with the highest rank. As [BitapFuzzySearchAlgorithm] is too low-level and
   * produces all matches it founds until the end of an input, this class contains business rules for selecting the best match.
   */
  private class BestOccurrenceSelector(private val myName: String) : BitapFuzzySearchAlgorithm.MatchConsumer {
    private var myBestRank = Int.MIN_VALUE
    private var myBestStart = 0
    private var myBestEnd = 0
    private var myBestErrorCount = 0

    /**
     * @param wordMatcher the matching engine what will produce matches to [consume].
     * @param typoAllowed whether this hump may still spend the single typo of the pattern.
     * @return the best occurrence this selector picked from [wordMatcher], or `null` when nothing matched.
     */
    fun selectIn(wordMatcher: BitapFuzzySearchAlgorithm, typoAllowed: Boolean): MatchedFragment? {
      // State is cleared because the consumer is reused.
      myBestRank = Int.MIN_VALUE
      myBestStart = 0
      myBestEnd = 0
      myBestErrorCount = 0

      wordMatcher.processMatches(myName, typoAllowed, this)
      if (myBestRank == Int.MIN_VALUE) return null

      return MatchedFragment(myBestStart, myBestEnd, myBestErrorCount)
    }

    /**
     * Receives occurrences and memorize the highest ranked one.
     *
     * The [rank] is **not** the score, but a "cheap" guess of how each occurrence would be compared to the others
     * to select a better candidate.
     *
     * Ranking in descending order of its weight:
     * 1. An occurrence without a typo;
     * 2. An occurrence at a word start;
     * 3. The leftmost occurrence;
     * 4. The longest occurrence (i.e., because the deletion typo matches a smaller fragment).
     */
    override fun consume(startOffset: Int, endOffsetExclusive: Int, errorCount: Int): Boolean {
      val rank = rank(startOffset, errorCount)

      val betterThanBest = when {
        rank != myBestRank -> rank > myBestRank
        startOffset != myBestStart -> startOffset < myBestStart
        else -> endOffsetExclusive > myBestEnd
      }
      if (betterThanBest) {
        myBestRank = rank
        myBestStart = startOffset
        myBestEnd = endOffsetExclusive
        myBestErrorCount = errorCount
      }

      // nothing beats the best rank, so reading the rest is pointless
      return rank < BEST_POSSIBLE_RANK
    }


    private fun rank(startOffset: Int, errorCount: Int): Int {
      var rank = 0
      if (errorCount == 0) rank = rank or RANK_NO_TYPO
      if (isAtWordStart(myName, startOffset)) rank = rank or RANK_WORD_START
      return rank
    }
  }

  companion object {
    private const val MIN_WORD_LENGTH_FOR_TYPO = 3

    private const val ERROR_PENALTY_WEIGHT = 2000.0

    private const val RANK_NO_TYPO = 1 shl 1

    private const val RANK_WORD_START = 1 shl 0

    private const val BEST_POSSIBLE_RANK = RANK_NO_TYPO or RANK_WORD_START

    private fun isAtWordStart(name: String, offset: Int): Boolean =
      offset in name.indices && NameUtilCore.isWordStart(name, offset)

    private fun isWildcard(c: Char): Boolean = c == '*' || c == ' '

    /**
     * Factory method for [BitapMinisculeMatcher].
     *
     * @param hardSeparators chars that also delimit the humps of [pattern].
     *
     * @return a matcher for [pattern], or `null` when the pattern carries no hump or at least one hump
     * cannot be served by [BitapFuzzySearchAlgorithm].
     */
    fun tryCreate(pattern: String, hardSeparators: CharArray): BitapMinisculeMatcher? {
      val words = splitPatternIntoWords(pattern, hardSeparators)
      if (words.isEmpty()) return null

      val wordMatchers = ArrayList<BitapFuzzySearchAlgorithm>(words.size)
      for (word in words) {
        wordMatchers.add(BitapFuzzySearchAlgorithm.tryCreate(word) ?: return null)
      }

      val scoringPattern = pattern.filterNot(::isWildcard).toCharArray()

      return BitapMinisculeMatcher(
        pattern = pattern,
        myPatternWordMatchers = wordMatchers,
        myScoringPattern = scoringPattern,
        myIsLowerCase = BooleanArray(scoringPattern.size) { scoringPattern[it].isLowerCase() },
        myIsUpperCase = BooleanArray(scoringPattern.size) { scoringPattern[it].isUpperCase() },
        myHardSeparators = hardSeparators,
      )
    }

    /**
     * Splits [pattern] into words by camel humps and by separators.
     */
    private fun splitPatternIntoWords(pattern: String, separators: CharArray): List<String> {
      val words = mutableListOf<String>()
      var index = 0
      while (index < pattern.length) {
        val c = pattern[index]
        if (isWildcard(c) || c in separators) {
          index++
          continue
        }
        val wordEnd = NameUtilCore.nextWord(pattern, index)
        words.add(pattern.substring(index, wordEnd))
        index = wordEnd
      }
      return words
    }
  }
}
