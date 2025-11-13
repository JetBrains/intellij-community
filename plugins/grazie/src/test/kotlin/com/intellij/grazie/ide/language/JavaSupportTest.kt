// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.openapi.util.Disposer
import com.intellij.spellchecker.ProjectDictionaryLayer
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.spellchecker.settings.SpellCheckerSettings
import com.intellij.testFramework.DumbModeTestUtils.runInDumbModeSynchronously
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import java.util.function.Consumer


class JavaSupportTest : GrazieTestBase() {

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

    (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).mustWaitForSmartMode(false, testRootDisposable)
    runInDumbModeSynchronously(project) { runHighlightTestForFile("ide/language/java/VectorablexxClass.java") }
  }

  fun `test spellchecking normalization`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    runHighlightTestForFile("ide/language/java/Normalization.java")
  }

  fun `test grazie spellchecking in java`() {
    val words = setOf("SSIZE_MAX", "MacTyppoo", "CANopen", "DBtune", "RESTTful", "typpoTypoo")
    ProjectDictionaryLayer(project).dictionary.addToDictionary(words)
    runHighlightTestForFile("ide/language/java/CamelCase.java")
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

  fun `test meaningful single suggestion in RenameTo action`() {
    myFixture.configureByText("a.java", """
      class A {
        void foo() {
          int <TYPO descr="Typo: In word 'tagret'">tag<caret>ret</TYPO>Dir = 1;
        }
      }
    """)
    myFixture.checkHighlighting()
    val intention = myFixture.findSingleIntention("Typo: Rename to 'targetDir'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      class A {
        void foo() {
          int targetDir = 1;
        }
      }
    """)
  }

  fun `test multiple suggestions in RenameTo action`() {
    myFixture.configureByText("a.java", """
      class A {
        void foo() {
          int <TYPO descr="Typo: In word 'barek'">barek<caret></TYPO> = 1;
        }
      }
    """)
    myFixture.checkHighlighting()
    val intention = myFixture.findSingleIntention("Typo: Rename to…")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      class A {
        void foo() {
          int bark = 1;
        }
      }
    """)
  }

  fun `test no highlighting after fixing an error within the same range`() {
    runHighlightTestForFile("ide/language/java/PDF.java")
    myFixture.launchAction(myFixture.findSingleIntention("PDF"))
    myFixture.checkHighlighting()
  }

  fun `test no highlighting inside of markdown code`() {
    runHighlightTestForFile("ide/language/java/MarkdownCode.java")
  }

  fun `test java keeps trailing spaces properly`() {
    runHighlightTestForFile("ide/language/java/Trailing.java")
  }

  @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
  fun `test add capitalized word to dictionary`() {
    val isUseSingleDictionary = SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave
    Disposer.register(testRootDisposable) {
      SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave = isUseSingleDictionary
    }
    SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave = true

    myFixture.configureByText("a.java", "// <TYPO descr=\"Typo: In word 'Qdrant'\">Qdra<caret>nt</TYPO>")
    myFixture.checkHighlighting()
    val intention = myFixture.findSingleIntention("Save 'Qdrant' to dictionary")
    myFixture.launchAction(intention)
    myFixture.configureByText("a.java", "// Qdrant")
    myFixture.checkHighlighting()
  }

  fun `test capitalized and uppercases words are not treated as typo if lowercase version is in the custom dictionary`() {
    SpellCheckerManager.getInstance(project).spellChecker!!.loadDictionary(object: Loader {
      override fun load(consumer: Consumer<String>) {
        consumer.accept("wexwex")
      }
      override fun getName(): String = "TestLoader"
    })

    myFixture.configureByText("a.java", "// wexwex, Wexwex, WEXWEX")
    myFixture.checkHighlighting()
  }

  fun `test performance on typos by word-level spellchecker`() {
    // German is not enabled on purpose to disable suggestion-based typo detection
    Benchmark.newBenchmark("word-level spellchecking performance") {
      runHighlightTestForFile("ide/language/java/Dictionary.java")
    }.setup {
      psiManager.dropPsiCaches()
      GrazieSpellCheckerEngine.getInstance(project).dropSuggestionCache()
    }.start()
  }

  private fun doTest(beforeText: String, afterText: String, hint: String) {
    myFixture.configureByText("a.java", beforeText)
    val intentionAction = myFixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResult(afterText)
  }
}
