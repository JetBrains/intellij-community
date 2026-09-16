// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.util.text.matching.BitapFuzzySearchAlgorithm
import com.intellij.util.text.matching.MatchedFragment
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitapFuzzySearchAlgorithmTest {

  @Test
  fun `processMatches should report an exact occurrence with no error`() {
    assertEquals(MatchedFragment(2, 8, 0), findMatch("Getter", "myGetterImpl"))
  }

  @Test
  fun `processMatches should report the exact occurrence of a name that also holds a fuzzy one`() {
    assertEquals(MatchedFragment(6, 12, 0), findMatch("Getter", "GutterGetter"))
  }

  @Test
  fun `processMatches should report the leftmost exact occurrence first`() {
    assertEquals(MatchedFragment(0, 3, 0), findMatch("foo", "foofoo"))
  }

  @Test
  fun `processMatches should report nothing when the name holds no occurrence`() {
    assertNull(findMatch("getter", "unrelated"))
  }

  @Test
  fun `processMatches should report an occurrence that spans the camel humps of the name`() {
    assertEquals(MatchedFragment(0, 12, 0), findMatch("identifierma", "IdentifierMatcher2"))
  }

  @Test
  fun `processMatches should charge one error for a substituted char`() {
    assertEquals(MatchedFragment(0, 6, 1), findMatch("getter", "gutter"))
  }

  @Test
  fun `processMatches should charge one error for a missing char`() {
    assertEquals(MatchedFragment(0, 5, 1), findMatch("getter", "etter"))
  }

  @Test
  fun `processMatches should charge one error for an extra char`() {
    assertEquals(MatchedFragment(0, 7, 1), findMatch("getter", "gettter"))
  }

  @Test
  fun `processMatches should charge one error for two transposed chars`() {
    assertEquals(MatchedFragment(0, 7, 1), findMatch("handler", "hnadler"))
  }

  @Test
  fun `processMatches should report a substitution before the other error routes`() {
    assertEquals(MatchedFragment(0, 6, 1), findMatch("getter", "Xetter"))
  }

  @Test
  fun `processMatches should report nothing when the name is two errors away`() {
    assertNull(findMatch("getter", "guttar"))
  }

  @Test
  fun `processMatches should ignore the case when it counts the errors`() {
    assertEquals(MatchedFragment(0, 6, 1), findMatch("Getter", "GUTTER"))
  }

  @Test
  fun `processMatches should match any name with one error for a single char pattern`() {
    assertEquals(MatchedFragment(0, 1, 1), findMatch("g", "xyz"))
  }

  @Test
  fun `processMatches should report offsets inside the name`() {
    for (pattern in listOf("a", "ab", "abc", "abcd")) {
      for (name in listOf("", "a", "ab", "b", "ba", "abc", "cab", "aab")) {
        val match = algorithm(pattern).firstMatch(name) ?: continue
        assertTrue(match.startOffset >= 0, "negative start offset for pattern='$pattern' name='$name': $match")
        assertTrue(match.endOffset <= name.length, "end offset past the name for pattern='$pattern' name='$name': $match")
      }
    }
  }

  @Test
  fun `processMatches should report every exact occurrence from left to right`() {
    assertEquals(listOf(MatchedFragment(3, 6), MatchedFragment(6, 9)),
                 algorithm("get").allMatches("WidgetGetter").filter { it.errorCount == 0 })
  }

  @Test
  fun `processMatches should report the occurrences in the order they end`() {
    val matches = algorithm("ab").allMatches("abcabab")
    assertTrue(matches.zipWithNext().all { (left, right) -> left.endOffset <= right.endOffset },
               "expected occurrences ordered by end offset, got $matches")
  }

  @Test
  fun `processMatches should hide an error occurrence that ends with an exact one`() {
    // a substitution may spend its error on a char that matches anyway, and would report the very text of (6, 12)
    assertEquals(listOf(MatchedFragment(6, 12)),
                 algorithm("Getter").allMatches("GutterGetter").filter { it.endOffset == 12 })
  }

  @Test
  fun `processMatches should stop when the consumer returns false`() {
    val seen = ArrayList<MatchedFragment>()
    val completed = algorithm("get").processMatches("WidgetGetter", true) { startOffset, endOffset, errorCount ->
      seen.add(MatchedFragment(startOffset, endOffset, errorCount))
      errorCount > 0 // stop on the first occurrence without an error
    }

    assertFalse(completed, "processMatches is expected to report that the consumer stopped it")
    assertEquals(MatchedFragment(3, 6), seen.last())
  }

  @Test
  fun `processMatches should return true when nobody stops the scan`() {
    assertTrue(algorithm("getter").processMatches("unrelated", true) { _, _, _ -> true })
  }

  /**
   * The differential test that guards the register arithmetic: whatever the Bitap core reports must agree with a brute
   * force optimal string alignment (Damerau-Levenshtein with adjacent transpositions) reference, in both directions -
   * neither missing a match that is within one error, nor inventing one that is not.
   */
  @Test
  fun `processMatches should agree with a brute force Damerau-Levenshtein reference`() {
    val random = Random(20260825)
    val alphabet = "abcab"

    repeat(20000) {
      val word = randomString(random, alphabet, minLength = 1, maxLength = 6)
      val name = randomString(random, alphabet, minLength = 0, maxLength = 10)
      val match = algorithm(word).firstMatch(name)

      val expectedExactStart = name.indexOf(word, ignoreCase = true)
      if (expectedExactStart >= 0) {
        assertEquals(MatchedFragment(expectedExactStart, expectedExactStart + word.length), match,
                     "wrong exact match of '$word' in '$name'")
        return@repeat
      }

      if (minDistanceToAnySubstring(word, name) > 1) {
        assertNull(match, "'$word' should not match '$name' within one error, got $match")
        return@repeat
      }

      assertNotNull(match, "'$word' should match '$name' within one error")
      val matchedText = name.substring(match.startOffset, match.endOffset)
      val distance = optimalStringAlignmentDistance(word.lowercase(), matchedText.lowercase())
      assertEquals(1, distance, "'$word' vs matched '$matchedText' is not exactly one error away")
      assertEquals(distance, match.errorCount, "reported error count disagrees with '$word' vs matched '$matchedText'")
    }
  }

  @Test
  fun `isSupported and tryCreate should refuse an empty pattern word`() {
    assertFalse(BitapFuzzySearchAlgorithm.isSupported(""))
    assertNull(BitapFuzzySearchAlgorithm.tryCreate(""))
  }

  @Test
  fun `isSupported and tryCreate should accept a pattern word of 64 chars`() {
    val longestSupported = "a".repeat(64)
    assertTrue(BitapFuzzySearchAlgorithm.isSupported(longestSupported))
    assertNotNull(BitapFuzzySearchAlgorithm.tryCreate(longestSupported))
  }

  @Test
  fun `isSupported and tryCreate should refuse a pattern word of 65 chars`() {
    val tooLong = "a".repeat(65)
    assertFalse(BitapFuzzySearchAlgorithm.isSupported(tooLong))
    assertNull(BitapFuzzySearchAlgorithm.tryCreate(tooLong))
  }

  private fun findMatch(patternWord: String, name: String): MatchedFragment? = algorithm(patternWord).firstMatch(name)

  /**
   * The rule the production code used before it started to enumerate the occurrences: an occurrence without an error
   * beats every occurrence with one, wherever it is, and the leftmost occurrence of either kind wins. It is kept here
   * because the assertions above were written against it.
   */
  private fun BitapFuzzySearchAlgorithm.firstMatch(name: String): MatchedFragment? {
    var exactMatch: MatchedFragment? = null
    var errorMatch: MatchedFragment? = null

    processMatches(name, true) { startOffset, endOffset, errorCount ->
      if (errorCount == 0) {
        exactMatch = MatchedFragment(startOffset, endOffset, 0)
        false
      }
      else {
        if (errorMatch == null) errorMatch = MatchedFragment(startOffset, endOffset, errorCount)
        true
      }
    }

    return exactMatch ?: errorMatch
  }

  private fun BitapFuzzySearchAlgorithm.allMatches(name: String): List<MatchedFragment> {
    val matches = ArrayList<MatchedFragment>()
    processMatches(name, true) { startOffset, endOffset, errorCount ->
      matches.add(MatchedFragment(startOffset, endOffset, errorCount))
      true
    }
    return matches
  }

  private fun algorithm(patternWord: String): BitapFuzzySearchAlgorithm =
    requireNotNull(BitapFuzzySearchAlgorithm.tryCreate(patternWord)) { "'$patternWord' is expected to be supported" }

  private fun randomString(random: Random, alphabet: String, minLength: Int, maxLength: Int): String {
    val length = random.nextInt(minLength, maxLength + 1)
    return buildString(length) {
      repeat(length) {
        val c = alphabet[random.nextInt(alphabet.length)]
        append(if (random.nextBoolean()) c.uppercaseChar() else c)
      }
    }
  }

  private fun minDistanceToAnySubstring(word: String, name: String): Int {
    val lowercaseWord = word.lowercase()
    val lowercaseName = name.lowercase()
    var best = Int.MAX_VALUE
    // only non-empty substrings: a match has to cover at least one char of the name to be reportable
    for (start in 0..lowercaseName.length) {
      for (end in start + 1..lowercaseName.length) {
        best = min(best, optimalStringAlignmentDistance(lowercaseWord, lowercaseName.substring(start, end)))
        if (best == 0) return 0
      }
    }
    return best
  }

  private fun optimalStringAlignmentDistance(left: String, right: String): Int {
    val distances = Array(left.length + 1) { IntArray(right.length + 1) }
    for (i in 0..left.length) distances[i][0] = i
    for (j in 0..right.length) distances[0][j] = j

    for (i in 1..left.length) {
      for (j in 1..right.length) {
        val substitutionCost = if (left[i - 1] == right[j - 1]) 0 else 1
        var best = min(min(distances[i - 1][j] + 1, distances[i][j - 1] + 1), distances[i - 1][j - 1] + substitutionCost)
        if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
          best = min(best, distances[i - 2][j - 2] + 1)
        }
        distances[i][j] = best
      }
    }
    return distances[left.length][right.length]
  }
}
