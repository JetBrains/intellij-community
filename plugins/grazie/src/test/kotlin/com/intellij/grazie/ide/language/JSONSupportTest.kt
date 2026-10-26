// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Test

@TestApplication
class JSONSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test grammar check in file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/json/Example.json")
  }

  @Test
  fun `test grammar check in file without literals`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    GrazieConfig.update { state ->
      state.copy(
        checkingContext = state.checkingContext.copy(
          isCheckInStringLiteralsEnabled = false,
        )
      )
    }
    grazie.runHighlightTestForFile("ide/language/json/Example_literals.json")
  }

  @PerformanceUnitTest
  @Test
  fun `test json typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.json file") {
      grazie.runHighlightTestForFile("ide/language/json/i18n.json")
    }.setup {
      grazie.dropPsiCaches()
      GrazieSpellCheckerEngine.getInstance(grazie.project).dropSuggestionCache()
    }.start()
  }
}
