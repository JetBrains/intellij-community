// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.polySymbolsTestsDataPath
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestFixtures
class BasicPolySymbolScopeTest {

  private val mock by polySymbolsMockQueryExecutorFixture("$polySymbolsTestsDataPath/query/list")

  @Test
  fun testExcludedModifierBelowThreshold() {
    doTestExcludedModifierIsRespected(symbolCount = 4)
  }

  @Test
  fun testExcludedModifierAtThreshold() {
    doTestExcludedModifierIsRespected(symbolCount = 5)
  }

  @Test
  fun testExcludedModifierAboveThreshold() {
    doTestExcludedModifierIsRespected(symbolCount = 6)
  }

  // IJPL-253020: below the linear-scan/SearchMap threshold, BasicPolySymbolScope used to only
  // filter by kind and ignored required/excluded modifiers and context, unlike the SearchMap path
  // used at or above the threshold. Query the scope directly (not through
  // PolySymbolQueryExecutor.listSymbolsQuery, which re-applies modifier filtering on its own
  // aggregated result and would mask the scope-level bug).
  private fun doTestExcludedModifierIsRespected(symbolCount: Int) {
    val scope = polySymbolScope {
      provides(TEST_KIND)
      initialize {
        addSymbol(TEST_KIND, "abstractSymbol") {
          modifiers(setOf(PolySymbolModifier.ABSTRACT))
        }
        repeat(symbolCount - 1) { i ->
          addSymbol(TEST_KIND, "plainSymbol$i") {}
        }
      }
    }
    val queryExecutor = mock.polySymbolQueryExecutorFactory.create(null)
    val stack = PolySymbolQueryStack()

    val listed = scope.getSymbols(
      TEST_KIND,
      PolySymbolListSymbolsQueryParams.create(queryExecutor, expandPatterns = false) {
        exclude(PolySymbolModifier.ABSTRACT)
      },
      stack,
    ).map { it.name }
    assertEquals(symbolCount - 1, listed.size,
                 "getSymbols() with $symbolCount symbols must drop the excluded-modifier symbol")
    assertFalse(listed.contains("abstractSymbol"),
                "getSymbols() with $symbolCount symbols must not return the ABSTRACT symbol")

    val matched = scope.getMatchingSymbols(
      TEST_KIND.withName("abstractSymbol"),
      PolySymbolNameMatchQueryParams.create(queryExecutor) {
        exclude(PolySymbolModifier.ABSTRACT)
      },
      stack,
    )
    assertTrue(matched.isEmpty(),
               "getMatchingSymbols() with $symbolCount symbols must not match the ABSTRACT symbol")

    val completions = scope.getCodeCompletions(
      TEST_KIND.withName(""),
      PolySymbolCodeCompletionQueryParams.create(queryExecutor, position = 0) {
        exclude(PolySymbolModifier.ABSTRACT)
      },
      stack,
    ).map { it.name }
    assertFalse(completions.contains("abstractSymbol"),
                "getCodeCompletions() with $symbolCount symbols must not offer the ABSTRACT symbol")
  }

  companion object {
    private val TEST_KIND: PolySymbolKind = PolySymbolKind["test", "testKind"]
  }
}
