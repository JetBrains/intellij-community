// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SeComposedWeightTest {

  @Test
  fun matchDecidesBeforeOtherKeys() {
    assertCompare(1, w(100, A to 0), w(50, A to 9))
  }

  @Test
  fun missingComponentCountsAsDefaultWeight() {
    assertCompare(0, w(100), w(100, A to 0))
    assertCompare(-1, w(100), w(100, A to 3))
    // The default of B is 5.
    assertCompare(0, w(100), w(100, B to 5))
    assertCompare(1, w(100), w(100, B to 4))
  }

  @Test
  fun keysAreComparedInTheirOrder() {
    // A goes before B, whatever the order in the list.
    assertCompare(-1, w(100, A to 0, B to 9), w(100, A to 1, B to 0))
    assertCompare(1, w(100, B to 6), w(100, A to 0, B to 5))
  }

  @Test
  fun idDecidesTheOrderOfKeysWithTheSameOrder() {
    val x = SeWeightKey("x", order = 10, defaultWeight = 0)
    val y = SeWeightKey("y", order = 10, defaultWeight = 0)

    assertEquals(listOf("matchWeight", "x", "y"), SeComposedWeight.of(listOf(c(y, 0), c(x, 0), SeWeightComponent(1))).components.map { it.id })
  }

  @Test
  fun keyEqualityAgreesWithComparison() {
    // A deserialized key is a new instance.
    val copy = SeWeightKey("notDeprecated", order = 200, defaultWeight = 1)

    assertEquals(SeWeightKey.NOT_DEPRECATED, copy)
    assertEquals(SeWeightKey.NOT_DEPRECATED.hashCode(), copy.hashCode())
    assertEquals(0, SeWeightKey.NOT_DEPRECATED.compareTo(copy))
    assertEquals(setOf(SeWeightKey.NOT_DEPRECATED), setOf(copy))
    assertNotEquals(SeWeightKey("notDeprecated", order = 201, defaultWeight = 1), copy)
  }

  @Test
  fun notDeprecatedIsTheDefault() {
    assertCompare(0, w(100), w(100, SeWeightKey.NOT_DEPRECATED to 1))
    assertCompare(1, w(100), w(100, SeWeightKey.NOT_DEPRECATED to 0))
  }

  @Test
  fun comparisonIsTransitive() {
    // These weights gave a cycle when the comparison skipped the missing ids.
    val first = w(0, A to 1, B to 2)
    val second = w(0, B to 1, C to 2)
    val third = w(0, C to 1, A to 2)
    assertContract(listOf(first, second, third))

    val values = listOf(0, 1, 5, 9)
    val keys = listOf(A, B, C)
    val weights = buildList {
      for (match in 0..1) {
        for (mask in 0..<(1 shl keys.size)) {
          for (value in values) {
            add(SeComposedWeight.of(listOf(SeWeightComponent(match)) + keys.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.map { c(it, value) }))
          }
        }
      }
    }
    assertContract(weights)
    // A sort checks the contract, too.
    assertEquals(weights.size, weights.sortedWith(naturalOrder()).size)
  }

  @Test
  fun withAddsComponentInKeyOrderAndReplacesSameKey() {
    val weight = w(100, B to 1).with(c(A, 2)).with(c(B, 7))

    assertEquals(listOf("matchWeight", "a", "b"), weight.components.map { it.id })
    assertEquals(listOf(100, 2, 7), weight.components.map { it.weight })
  }

  @Test
  fun constructorRejectsInvalidComponents() {
    assertThrows<IllegalArgumentException> { SeComposedWeight.of(listOf(SeWeightComponent(1), c(A, 0), c(A, 1))) }
    assertThrows<IllegalArgumentException> { SeComposedWeight.of(listOf(c(A, 0))) }
    assertThrows<IllegalArgumentException> { SeWeightKey("negative", order = -1, defaultWeight = 0) }
  }

  /** Checks the [Comparable] contract for every pair and every triple of [weights]. */
  private fun assertContract(weights: List<SeComposedWeight>) {
    for (x in weights) {
      for (y in weights) {
        val xy = x.compareTo(y).sign
        assertEquals(-xy, y.compareTo(x).sign, "$x vs $y")
        for (z in weights) {
          val yz = y.compareTo(z).sign
          if (xy >= 0 && yz >= 0) {
            assertTrue(x >= z, "$x >= $y >= $z")
          }
          if (xy == 0) {
            assertEquals(x.compareTo(z).sign, y.compareTo(z).sign, "$x == $y, compared with $z")
          }
        }
      }
    }
  }

  /** Checks that `left.compareTo(right)` has the sign of [expected], and that the reverse comparison has the opposite sign. */
  private fun assertCompare(expected: Int, left: SeComposedWeight, right: SeComposedWeight) {
    assertEquals(expected, left.compareTo(right).sign, "$left vs $right")
    assertEquals(-expected, right.compareTo(left).sign, "$right vs $left")
  }

  private fun w(match: Int, vararg components: Pair<SeWeightKey, Int>): SeComposedWeight =
    SeComposedWeight.of(listOf(SeWeightComponent(match)) + components.map { (key, weight) -> c(key, weight) })

  private fun c(key: SeWeightKey, weight: Int): SeWeightComponent = SeWeightComponent(key, weight)

  private val Int.sign: Int get() = Integer.signum(this)

  private companion object {
    val A = SeWeightKey("a", order = 10, defaultWeight = 0)
    val B = SeWeightKey("b", order = 20, defaultWeight = 5)
    val C = SeWeightKey("c", order = 30, defaultWeight = 0)
  }
}
