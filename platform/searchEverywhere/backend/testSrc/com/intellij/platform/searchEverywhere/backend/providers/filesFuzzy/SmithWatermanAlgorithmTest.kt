// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.ide.actions.searcheverywhere.fuzzyMatching.ScoringParameters
import com.intellij.ide.actions.searcheverywhere.fuzzyMatching.SmithWatermanAlgorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for SmithWatermanAlgorithm.
 */
class SmithWatermanAlgorithmTest {

  @Test
  fun testExactMatch() {
    val result = SmithWatermanAlgorithm.match("test", "test")
    assertTrue(result.hasMatch())
    assertTrue(result.score > 0)
    assertEquals(listOf(0, 1, 2, 3), result.matchedIndices)
  }

  @Test
  fun testPartialMatch() {
    val result = SmithWatermanAlgorithm.match("tst", "test")
    assertTrue(result.hasMatch())
    assertTrue(result.score > 0)
    assertEquals(3, result.matchedIndices.size)
  }

  @Test
  fun testNoMatch() {
    val result = SmithWatermanAlgorithm.match("xyz", "abc")
    assertEquals(0, result.score)
    assertEquals(emptyList<Int>(), result.matchedIndices)
  }

  @Test
  fun testEmptyPattern() {
    val result = SmithWatermanAlgorithm.match("", "test")
    assertEquals(0, result.score)
  }

  @Test
  fun testEmptyTarget() {
    val result = SmithWatermanAlgorithm.match("test", "")
    assertEquals(0, result.score)
  }

  @Test
  fun testCaseInsensitiveMatching() {
    val result1 = SmithWatermanAlgorithm.match("test", "TEST")
    val result2 = SmithWatermanAlgorithm.match("TEST", "test")
    val result3 = SmithWatermanAlgorithm.match("TeSt", "tEsT")

    assertTrue(result1.hasMatch())
    assertTrue(result2.hasMatch())
    assertTrue(result3.hasMatch())
  }

  @Test
  fun testCamelCaseBonus() {
    val result = SmithWatermanAlgorithm.match("GC", "GotoClass")
    assertTrue(result.hasMatch())
    assertTrue(result.matchedIndices.contains(0))
    assertTrue(result.matchedIndices.contains(4))
  }

  @Test
  fun testSeparatorBonus() {
    val result = SmithWatermanAlgorithm.match("fm", "File_Manager")
    assertTrue(result.hasMatch())
    assertTrue(result.matchedIndices.contains(0))
    assertTrue(result.matchedIndices.contains(5))
  }

  @Test
  fun testFirstCharacterBonus() {
    val result1 = SmithWatermanAlgorithm.match("t", "test")
    val result2 = SmithWatermanAlgorithm.match("t", "atest")

    assertTrue(result1.score > result2.score)
  }

  @Test
  fun testFzfExampleGcfMatchesGotoClassFile() {
    val result = SmithWatermanAlgorithm.match("gcf", "GotoClassFile.kt")
    assertTrue(result.hasMatch())
    assertTrue(result.score > 50)
    assertTrue(result.normalizedScore > 0.5)
  }

  @Test
  fun testFzfExampleSbrMatchesSearchEverywhereBaseRenderer() {
    val result = SmithWatermanAlgorithm.match("sbr", "SearchEverywhereBaseRenderer.java")
    assertTrue(result.hasMatch())
    assertTrue(result.score > 50)
  }

  @Test
  fun testConsecutiveMatchBonus() {
    val result = SmithWatermanAlgorithm.match("ab", "abc")
    assertTrue(result.hasMatch())
    assertEquals(listOf(0, 1), result.matchedIndices)
    val params = ScoringParameters()
    val expectedMinScore = params.matchScore * 2 + params.firstCharBonus + params.consecutiveBonus
    assertTrue(result.score >= expectedMinScore)
  }

  @Test
  fun testNormalizedScoreRange() {
    val result = SmithWatermanAlgorithm.match("test", "test")
    assertTrue(result.normalizedScore >= 0.0)
    assertTrue(result.normalizedScore <= 1.0)
  }

  @Test
  fun testExactMatchScoresAtLeastAsHighAsLongerTarget() {
    val exactMatch = SmithWatermanAlgorithm.match("Owner", "Owner")
    val longerTarget = SmithWatermanAlgorithm.match("Owner", "OwnerRepository")
    assertTrue(exactMatch.score >= longerTarget.score,
      "Exact match (${exactMatch.score}) should score >= longer target (${longerTarget.score})")
  }

  @Test
  fun testPatternLongerThanTarget() {
    val result = SmithWatermanAlgorithm.match("verylongpattern", "short")
    assertEquals(0, result.score)
  }

  @Test
  fun testShorterTargetScoresHigherThanLongerTargetWithSameMatch() {
    val ownerExamples = listOf("Owner",
                               "OwnerRepository",
                               "OwnerPet2",
                               "OwnerPet22",
                               "OwnerPetClinic",
                               "OwnerHouseWithSomePet",
                               "OwnerHouse",
                               "Owner2",
                               "Owner22",
                               "Owner234")

    val expected = ownerExamples.sortedBy { it.length }
    val actual = ownerExamples.sortedByDescending { SmithWatermanAlgorithm.match("owner", it).score }
    assertEquals(expected, actual, "Shorter target score should be higher than longer target score")

    val actualNormalized = ownerExamples.sortedByDescending { SmithWatermanAlgorithm.match("owner", it).normalizedScore }
    assertEquals(expected, actualNormalized, "Shorter target normalized score should be higher than longer target normalized score")
  }
}
