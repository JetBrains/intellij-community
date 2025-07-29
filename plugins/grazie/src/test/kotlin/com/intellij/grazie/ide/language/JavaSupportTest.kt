// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.spellchecker.ProjectDictionaryLayer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark


class JavaSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf("LanguageTool.EN.UPPERCASE_SENTENCE_START")

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_LATEST
  }

  fun `test spellcheck in constructs`() {
    runHighlightTestForFile("ide/language/java/Constructs.java")
  }

  fun `test grammar check in docs`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/java/Docs.java")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("ide/language/java/StringLiterals.java")
  }

  fun `test grammar check in comments`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.UKRAINIAN, Lang.BELARUSIAN))
    runHighlightTestForFile("ide/language/java/Comments.java")
  }

  fun `test split line quick fix`() {
    runHighlightTestForFile("ide/language/java/SplitLine.java")
    myFixture.launchAction(myFixture.findSingleIntention(", but"))
    myFixture.checkResultByFile("ide/language/java/SplitLine_after.java")
  }

  fun `test do not merge text with non-text`() {
    runHighlightTestForFile("ide/language/java/AccidentalMerge.java")
    myFixture.launchAction(myFixture.findSingleIntention("Remove"))
    myFixture.checkResultByFile("ide/language/java/AccidentalMerge_after.java")
  }

  fun `test long comment performance`() {
    Benchmark.newBenchmark("highlighting") {
      runHighlightTestForFile("ide/language/java/LongCommentPerformance.java")
    }.setup { psiManager.dropPsiCaches() }.start()
  }

  fun `test performance with many line comments`() {
    val text = "// this is a single line comment\n".repeat(5000)
    myFixture.configureByText("a.java", text)
    Benchmark.newBenchmark("highlighting") {
      myFixture.checkHighlighting()
    }.setup { psiManager.dropPsiCaches() }.start()
  }

  fun testCommentIsNotHighlightedIfThereIsReference() {
    runHighlightTestForFile("ide/language/java/VectorablexxClass.java")
  }

  fun `test spellchecking normalization`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    runHighlightTestForFile("ide/language/java/Normalization.java")
  }

  fun `test grazie spellchecking in java`() {
    val words = setOf("SSIZE_MAX", "MacTyppoo", "CANopen", "DBtune", "RESTTful", "typpoTypoo")
    ProjectDictionaryLayer(project).dictionary.addToDictionary(words)
    runHighlightTestForFileUsingGrazieSpellchecker("ide/language/java/CamelCase.java")
  }

  fun `test multiline compounds`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    doTest(
      """
        public class Main {
          /**
           * I use {@code awaitility} to poll any eve<caret>ntually-      
           * consistent results for a short period.
           */
          int consistency;
        }
      """.trimIndent(),
      """
        public class Main {
          /**
           * I use {@code awaitility} to poll any eventually-consistent results for a short period.
           */
          int consistency;
        }
      """.trimIndent(),
      "eventually-consistent"
    )
    doTest(
      """
        public class Main {
          // Du bestellst ein Paket bei einem Online         
          // -Sh<caret>op. Direkt nach der Bestellung steht auf der Website.
          double onlineShop;
        }
      """.trimIndent(),
      """
        public class Main {
          // Du bestellst ein Paket bei einem Online-Shop. Direkt nach der Bestellung steht auf der Website.
          double onlineShop;
        }
      """.trimIndent(),
      "Online-Shop"
      )
  }

  private fun doTest(beforeText: String, afterText: String, hint: String) {
    myFixture.configureByText("a.java", beforeText)
    val intentionAction = myFixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResult(afterText)
  }
}
