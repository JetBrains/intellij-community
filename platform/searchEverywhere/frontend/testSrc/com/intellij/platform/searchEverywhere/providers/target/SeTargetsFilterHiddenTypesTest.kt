// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.searchEverywhere.SeFilterState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the hidden types of [SeTargetsFilter].
 *
 * A provider can show several type filters, and it names each one with a key. So the filter keeps one
 * hidden list per key, and one filter must never erase what another one hides.
 */
class SeTargetsFilterHiddenTypesTest {

  @Test
  fun cloneWithKeepsEveryOtherFilter() {
    // The regression this shape prevents: a DBMS toggle used to drop every hidden object kind.
    val filter = filterOf(DBMS to listOf("PostgreSQL"), KIND to listOf("Columns", "Indices"))

    val updated = filter.cloneWith(KIND, listOf("Columns"))

    assertEquals(mapOf(DBMS to listOf("PostgreSQL"), KIND to listOf("Columns")), updated.hiddenTypes)
  }

  @Test
  fun cloneWithAddsAKeyThatWasAbsent() {
    val updated = filterOf().cloneWith(KIND, listOf("Roles"))

    assertEquals(mapOf(KIND to listOf("Roles")), updated.hiddenTypes)
  }

  @Test
  fun cloneWithKeepsTheScope() {
    val filter = SeTargetsFilter("scope-1", true, mapOf(KIND to listOf("Tables")))

    val updated = filter.cloneWith(KIND, emptyList())

    assertEquals("scope-1", updated.selectedScopeId)
    assertEquals(true, updated.isAutoTogglePossible)
  }

  @Test
  fun theStateKeepsEveryKey() {
    val filter = filterOf(DBMS to listOf("PostgreSQL"), KIND to listOf("Columns", "Indices"))

    assertEquals(filter.hiddenTypes, roundTrip(filter).hiddenTypes)
  }

  @Test
  fun theStateKeepsAKeyThatHidesNothing() {
    val filter = filterOf(DBMS to listOf("PostgreSQL"), KIND to emptyList())

    assertEquals(filter.hiddenTypes, roundTrip(filter).hiddenTypes)
  }

  @Test
  fun theStateKeepsAKeyThatLooksLikeAnotherOne() {
    // The state stores every key under one prefix, so a key must not swallow a longer one.
    val filter = filterOf(DBMS to listOf("PostgreSQL"), "${DBMS}Extra" to listOf("MySQL"))

    assertEquals(filter.hiddenTypes, roundTrip(filter).hiddenTypes)
  }

  @Test
  fun theStateKeepsTheScopeAndTheToggle() {
    val filter = SeTargetsFilter("scope-1", true, mapOf(KIND to listOf("Tables")))

    val restored = roundTrip(filter)

    assertEquals("scope-1", restored.selectedScopeId)
    assertEquals(true, restored.isAutoTogglePossible)
  }

  @Test
  fun anEmptyStateHidesNothing() {
    val restored = SeTargetsFilter.from(SeFilterState.Empty)

    assertEquals(emptyMap<String, List<String>>(), restored.hiddenTypes)
    assertEquals(null, restored.selectedScopeId)
    assertEquals(false, restored.isAutoTogglePossible)
  }

  @Test
  fun aFilterThatHidesNothingSurvivesTheState() {
    assertEquals(emptyMap<String, List<String>>(), roundTrip(filterOf()).hiddenTypes)
  }

  private fun filterOf(vararg hiddenTypes: Pair<String, List<String>>): SeTargetsFilter =
    SeTargetsFilter(selectedScopeId = null, isAutoTogglePossible = false, hiddenTypes = mapOf(*hiddenTypes))

  private fun roundTrip(filter: SeTargetsFilter): SeTargetsFilter = SeTargetsFilter.from(filter.toState())

  private companion object {
    private const val DBMS = "dbms"
    private const val KIND = "dbObjectKind"
  }
}
