// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.components.service
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.tools.ide.metrics.benchmark.Benchmark

class YamlSupportTest : GrazieTestBase() {
  override val enableGrazieChecker: Boolean = true

  fun `test grammar check in yaml file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/yaml/Example.yaml")
  }

  fun `test text extraction`() {
    val file = myFixture.configureByText("a.yaml", "foo: 'bar'")
    assertEquals("bar", TextExtractor.findTextAt(file, 6, TextContent.TextDomain.ALL).toString())
  }

  fun `test yaml typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.yaml file") {
      runHighlightTestForFile("ide/language/yaml/i18n.yaml")
    }.setup {
      psiManager.dropPsiCaches()
      project.service<GrazieSpellCheckerEngine>().dropSuggestionCache()
    }.start()
  }
}
