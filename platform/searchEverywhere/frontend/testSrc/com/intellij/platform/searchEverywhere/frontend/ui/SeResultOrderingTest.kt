// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

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

  private fun insert(
    newProvider: Int, newExact: Boolean, newWeight: Int,
    itemProvider: Int, itemExact: Boolean, itemWeight: Int,
  ): Boolean = shouldInsertAbove(
    newProviderPriority = newProvider,
    newIsExactMatch = newExact,
    newWeight = newWeight,
    itemProviderPriority = itemProvider,
    itemIsExactMatch = itemExact,
    itemWeight = itemWeight,
    prioritizeExactMatch = true
  )
}
