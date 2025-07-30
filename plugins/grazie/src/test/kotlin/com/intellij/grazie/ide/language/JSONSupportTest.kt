// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.components.service
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.tools.ide.metrics.benchmark.Benchmark

class JSONSupportTest : GrazieTestBase() {
  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/json/Example.json")
  }

  fun `test json typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.json file") {
      runHighlightTestForFile("ide/language/json/i18n.json")
    }.setup {
      psiManager.dropPsiCaches()
      project.service<GrazieSpellCheckerEngine>().dropSuggestionCache()
    }.start()
  }
}
