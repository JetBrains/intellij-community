// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeWeightComponent
import com.intellij.platform.searchEverywhere.SeWeightKey
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the result-list insertion ordering used by Search Everywhere, covering the exact-match sub-tier that
 * keeps an exact match of the typed text above partial/fuzzy/ML-reweighted siblings of the same provider priority.
 */
class SeResultOrderingTest {

  @Test
  fun higherProviderPriorityAlwaysWins() {
    // A pinned provider (e.g. Calculator/TopHit) stays on top even against a lower-priority exact match.
    assertTrue(insert(newProvider = 5, newExact = false, newWeight = 0, itemProvider = 3, itemExact = true, itemWeight = 9999))
    assertFalse(insert(newProvider = 3, newExact = true, newWeight = 9999, itemProvider = 5, itemExact = false, itemWeight = 0))
  }

  @Test
  fun exactMatchWinsOverHigherWeightWithinSameProviderPriority() {
    // The whole point: an exact match outranks a heavier partial/fuzzy result of the same provider.
    assertTrue(insert(newProvider = 0, newExact = true, newWeight = 1, itemProvider = 0, itemExact = false, itemWeight = 9999))
    assertFalse(insert(newProvider = 0, newExact = false, newWeight = 9999, itemProvider = 0, itemExact = true, itemWeight = 1))
  }

  @Test
  fun weightBreaksTieWhenExactnessIsEqual() {
    // Both exact -> weight decides.
    assertTrue(insert(newProvider = 0, newExact = true, newWeight = 100, itemProvider = 0, itemExact = true, itemWeight = 50))
    assertFalse(insert(newProvider = 0, newExact = true, newWeight = 50, itemProvider = 0, itemExact = true, itemWeight = 100))
    // Neither exact -> unchanged legacy behavior (pure weight comparison).
    assertTrue(insert(newProvider = 0, newExact = false, newWeight = 100, itemProvider = 0, itemExact = false, itemWeight = 50))
    assertFalse(insert(newProvider = 0, newExact = false, newWeight = 100, itemProvider = 0, itemExact = false, itemWeight = 100))
  }

  @Test
  fun nextWeightComponentBreaksTieOfFirstComponent() {
    // Recent files: the same matching degree, so the recency decides.
    assertTrue(insertComposed(newWeight = composed(100, 7), itemWeight = composed(100, 3)))
    assertFalse(insertComposed(newWeight = composed(100, 3), itemWeight = composed(100, 7)))
    // The first component wins over every next component.
    assertFalse(insertComposed(newWeight = composed(50, 999), itemWeight = composed(100, 0)))
  }

  @Test
  fun higherMinorPriorityBreaksTieOfWeight() {
    // As in the old Search Everywhere: a class (priority 9900) goes above a file (priority 9800) with the same weight.
    assertTrue(insertRanked(newWeight = 100, newMinorPriority = 9900, itemWeight = 100, itemMinorPriority = 9800))
    assertFalse(insertRanked(newWeight = 100, newMinorPriority = 9800, itemWeight = 100, itemMinorPriority = 9900))
    // An equal minor priority keeps the arrival order.
    assertFalse(insertRanked(newWeight = 100, newMinorPriority = 9900, itemWeight = 100, itemMinorPriority = 9900))
    // The weight wins over the minor priority.
    assertFalse(insertRanked(newWeight = 50, newMinorPriority = 9900, itemWeight = 100, itemMinorPriority = 9800))
  }

  private fun insert(
    newProvider: Int, newExact: Boolean, newWeight: Int,
    itemProvider: Int, itemExact: Boolean, itemWeight: Int,
  ): Boolean = isAbove(
    SeResultOrderingKey(newProvider, newExact, SeComposedWeight(newWeight), 0),
    SeResultOrderingKey(itemProvider, itemExact, SeComposedWeight(itemWeight), 0),
  )

  private fun insertComposed(newWeight: SeComposedWeight, itemWeight: SeComposedWeight): Boolean = isAbove(
    SeResultOrderingKey(0, false, newWeight, 0),
    SeResultOrderingKey(0, false, itemWeight, 0),
  )

  private fun insertRanked(newWeight: Int, newMinorPriority: Int, itemWeight: Int, itemMinorPriority: Int): Boolean = isAbove(
    SeResultOrderingKey(0, false, SeComposedWeight(newWeight), newMinorPriority),
    SeResultOrderingKey(0, false, SeComposedWeight(itemWeight), itemMinorPriority),
  )

  private fun isAbove(newKey: SeResultOrderingKey, itemKey: SeResultOrderingKey): Boolean =
    newKey.compare(itemKey, prioritizeExactMatch = true) > 0

  private fun composed(match: Int, recency: Int): SeComposedWeight =
    SeComposedWeight.of(listOf(SeWeightComponent(match), SeWeightComponent(SeWeightKey.RECENCY, recency)))
}
