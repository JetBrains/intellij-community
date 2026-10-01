// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeComposedWeightTest {

  @Test
  fun equalPrefixMakesWeightsOfDifferentSizesEqual() {
    assertCompare(0, w("m" to 100), w("m" to 100, "r" to 5))
  }

  @Test
  fun commonPrefixDecidesForDifferentSizes() {
    assertCompare(1, w("m" to 100), w("m" to 50, "r" to 5))
    assertCompare(-1, w("m" to 100, "r" to 3), w("m" to 100, "r" to 7, "x" to 1))
  }

  @Test
  fun differentIdsAtNonFirstPositionMakeWeightsEqual() {
    assertCompare(0, w("m" to 100, "r" to 5), w("m" to 100, "x" to 7))
  }

  @Test
  fun differentIdsAtNonFirstPositionStopComparison() {
    // The third components differ, but the id mismatch at index 1 comes first.
    assertCompare(0, w("m" to 100, "r" to 5, "z" to 1), w("m" to 100, "x" to 5, "z" to 9))
  }

  @Test
  fun firstComponentDecidesBeforeIdsAtNonFirstPosition() {
    assertCompare(1, w("m" to 100, "r" to 5), w("m" to 50, "x" to 7))
  }

  @Test
  fun idsAtFirstPositionAreIgnored() {
    assertCompare(1, w("a" to 100), w("b" to 50))
    assertCompare(0, w("a" to 100), w("b" to 100))
  }

  @Test
  fun comparisonContinuesAfterFirstComponentsWithDifferentIds() {
    assertCompare(-1, w("a" to 100, "r" to 3), w("b" to 100, "r" to 7))
  }

  /** Checks that `left.compareTo(right)` has the sign of [expected], and that the reverse comparison has the opposite sign. */
  private fun assertCompare(expected: Int, left: SeComposedWeight, right: SeComposedWeight) {
    assertEquals(expected, left.compareTo(right).sign, "$left vs $right")
    assertEquals(-expected, right.compareTo(left).sign, "$right vs $left")
  }

  private fun w(vararg components: Pair<String, Int>): SeComposedWeight =
    SeComposedWeight(components.map { (id, weight) -> SeWeightComponent(id, weight) })

  private val Int.sign: Int get() = Integer.signum(this)
}
