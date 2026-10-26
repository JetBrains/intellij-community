// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class YamlSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test grammar check in yaml file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/yaml/Example.yaml")
  }

  @Test
  fun `test text extraction`() {
    val file = grazie.fixture.configureByText("a.yaml", "foo: 'bar'")
    assertEquals("bar", runReadActionBlocking { TextExtractor.findTextAt(file, 6, TextContent.TextDomain.ALL) }.toString())
  }

  @PerformanceUnitTest
  @Test
  fun `test yaml typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.yaml file") {
      grazie.runHighlightTestForFile("ide/language/yaml/i18n.yaml")
    }.setup {
      grazie.dropPsiCaches()
      GrazieSpellCheckerEngine.getInstance(grazie.project).dropSuggestionCache()
    }.start()
  }
}
