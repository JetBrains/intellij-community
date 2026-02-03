// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark

class JSONSupportTest : GrazieTestBase() {

  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/json/Example.json")
  }

  fun `test grammar check in file without literals`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    GrazieConfig.update { state ->
      state.copy(
        checkingContext = state.checkingContext.copy(
          isCheckInStringLiteralsEnabled = false,
        )
      )
    }
    runHighlightTestForFile("ide/language/json/Example_literals.json")
  }

  @PerformanceUnitTest
  fun `test json typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.json file") {
      runHighlightTestForFile("ide/language/json/i18n.json")
    }.setup {
      psiManager.dropPsiCaches()
      GrazieSpellCheckerEngine.getInstance(project).dropSuggestionCache()
    }.start()
  }
}
