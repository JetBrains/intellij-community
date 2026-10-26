// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("NonAsciiCharacters")

package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.unknownOffsets
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.spellchecker.ProjectDictionaryLayer
import com.intellij.spellchecker.settings.SpellCheckerSettings
import com.intellij.testFramework.DumbModeTestUtils.runInDumbModeSynchronously
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow


@TestApplication
class JavaSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture, projectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_LATEST)

  @Test
  fun `test spellcheck in constructs`() {
    grazie.runHighlightTestForFile("ide/language/java/Constructs.java")
  }

  @Test
  fun `test grammar check in docs`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/java/Docs.java")
  }

  @Test
  fun `test grammar check in string literals`() {
    grazie.runHighlightTestForFile("ide/language/java/StringLiterals.java")
  }

  @Test
  fun `test grammar check in comments`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.UKRAINIAN, Lang.BELARUSIAN))
    grazie.runHighlightTestForFile("ide/language/java/Comments.java")
  }

  @Test
  fun `test split line quick fix`() {
    grazie.runHighlightTestForFile("ide/language/java/SplitLine.java")
    grazie.fixture.launchAction(grazie.fixture.findSingleIntention(", but"))
    grazie.fixture.checkResultByFile("ide/language/java/SplitLine_after.java")
  }

  @Test
  fun `test do not merge text with non-text`() {
    grazie.runHighlightTestForFile("ide/language/java/AccidentalMerge.java")
    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("Remove"))
    grazie.fixture.checkResultByFile("ide/language/java/AccidentalMerge_after.java")
  }

  @Test
  fun `test escape sequences in string literals`() {
    grazie.fixture.configureByText("a.java", """
      class Scratch {
          public static void main(String[] args) {
              var value1 = ""${'"'}
                      wrong object class\nexpected 1\nactual: 2""${'"'};
          }
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test escaped backslash before text`() {
    grazie.fixture.configureByText("a.java", """
      class Repro {
        String path = "C:\\transform-templates";
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @PerformanceUnitTest
  @Test
  fun `test long comment performance`() {
    Benchmark.newBenchmark("highlighting") {
      grazie.runHighlightTestForFile("ide/language/java/LongCommentPerformance.java")
    }.setup { grazie.dropPsiCaches() }.start()
  }

  @PerformanceUnitTest
  @Test
  fun `test performance with many line comments`() {
    val text = "// this is a single line comment\n".repeat(5000)
    grazie.fixture.configureByText("a.java", text)
    Benchmark.newBenchmark("highlighting") {
      grazie.fixture.checkHighlighting()
    }.setup { grazie.dropPsiCaches() }.start()
  }

  @Test
  fun testCommentIsNotHighlightedIfThereIsReference() {
    grazie.runHighlightTestForFile("ide/language/java/VectorablexxClass.java")

    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, grazie.testRootDisposable)
    runInDumbModeSynchronously(grazie.project) { grazie.runHighlightTestForFile("ide/language/java/VectorablexxClass.java") }
  }

  @Test
  fun `test spellchecking normalization`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    grazie.runHighlightTestForFile("ide/language/java/Normalization.java")
  }

  @Test
  fun `test grazie spellchecking in java`() {
    val words = setOf("SSIZE_MAX", "MacTyppoo", "CANopen", "DBtune", "RESTTful", "typpoTypoo")
    ProjectDictionaryLayer(grazie.project).dictionary.addToDictionary(words)
    grazie.runHighlightTestForFile("ide/language/java/CamelCase.java")
  }

  @Test
  fun `test multiline compounds`() {
    doTest(
      """
        public class Main {
          /**
           * It is a very good number: twent<caret>y-
           * one.
           */
          int number;
        }
      """.trimIndent(),
      """
        public class Main {
          /**
           * It is a very good number: twenty-one.
           */
          int number;
        }
      """.trimIndent(),
      "twenty-one"
    )
    doTest(
      """
        public class Main {
          /**
           * It is a very good number: twenty
           * -o<caret>ne.
           */
          int number;
        }
      """.trimIndent(),
      """
        public class Main {
          /**
           * It is a very good number: twenty-one.
           */
          int number;
        }
      """.trimIndent(),
      "twenty-one"
    )
  }

  @Test
  fun `test meaningful single suggestion in RenameTo action`() {
    grazie.fixture.configureByText("a.java", """
      class A {
        void foo() {
          int <TYPO descr="Typo: In word 'tagret'">tag<caret>ret</TYPO>Dir = 1;
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    val intention = grazie.fixture.findSingleIntention("Typo: Rename to 'targetDir'")
    grazie.fixture.launchAction(intention)
    grazie.fixture.checkResult("""
      class A {
        void foo() {
          int targetDir = 1;
        }
      }
    """)
  }

  @Test
  fun `test multiple suggestions in RenameTo action`() {
    grazie.fixture.configureByText("a.java", """
      class A {
        void foo() {
          int <TYPO descr="Typo: In word 'barek'">barek<caret></TYPO> = 1;
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    val intention = grazie.fixture.findSingleIntention("Typo: Rename to…")
    grazie.fixture.launchAction(intention)
    grazie.fixture.checkResult("""
      class A {
        void foo() {
          int bark = 1;
        }
      }
    """)
  }

  @Test
  fun `test no highlighting after fixing an error within the same range`() {
    grazie.runHighlightTestForFile("ide/language/java/PDF.java")
    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("PDF"))
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no highlighting inside of markdown code`() {
    grazie.runHighlightTestForFile("ide/language/java/MarkdownCode.java")
  }

  @Test
  fun `test text extraction from markdown doc reference link`() {
    val text = """
      class A {
        /// Please do not use directly; use [EventFields#Class(String)] instead.
        void foo() {}
      }
    """.trimIndent()
    val file = grazie.fixture.configureByText("a.java", text)
    val content = runReadActionBlocking { TextExtractor.findTextAt(file, text.indexOf("Please"), TextContent.TextDomain.ALL) }
    assertEquals("Please do not use directly; use | instead.", unknownOffsets(content!!))
    assertTrue(content.markupOffsets().isEmpty())
  }

  @Test
  fun `test text extraction from markdown doc inline link preserves label`() {
    val text = """
      class A {
        /// Please read [the manual](https://example.com/manual) before use.
        void foo() {}
      }
    """.trimIndent()
    val file = grazie.fixture.configureByText("a.java", text)
    val content = runReadActionBlocking { TextExtractor.findTextAt(file, text.indexOf("Please"), TextContent.TextDomain.ALL) }
    assertEquals("Please read the manual before use.", unknownOffsets(content!!))
    assertArrayEquals(intArrayOf("Please read ".length, "Please read the manual".length), content.markupOffsets())
  }

  @Test
  fun `test java keeps trailing spaces properly`() {
    grazie.runHighlightTestForFile("ide/language/java/Trailing.java")
  }

  @Test
  fun `test add capitalized word to dictionary`() {
    val project = grazie.project
    val isUseSingleDictionary = SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave
    Disposer.register(grazie.testRootDisposable) {
      SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave = isUseSingleDictionary
    }
    SpellCheckerSettings.getInstance(project).isUseSingleDictionaryToSave = true

    grazie.fixture.configureByText("a.java", "// <TYPO descr=\"Typo: In word 'Blahblahwordish'\">Blahblahword<caret>ish</TYPO>")
    grazie.fixture.checkHighlighting()
    val intention = grazie.fixture.findSingleIntention("Save 'Blahblahwordish' to dictionary")
    grazie.fixture.launchAction(intention)
    grazie.fixture.configureByText("a.java", "// Blahblahwordish")
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test capitalized and uppercases words are not treated as typo if lowercase version is saved to a dictionary`() {
    grazie.saveWord("wexwex")
    grazie.fixture.configureByText("a.java", "// wexwex, Wexwex, WEXWEX")
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test capitalized and uppercases words are not treated as typo if capitalized version is saved to a dictionary`() {
    grazie.saveWord("Wexwex")
    grazie.fixture.configureByText("a.java", "// wexwex, Wexwex, WEXWEX")
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test capitalized and uppercases words are treated as typo if mixed case version is saved to a dictionary`() {
    grazie.saveWord("WexWex")
    grazie.fixture.configureByText("a.java",
      """
        // <TYPO descr="Typo: In word 'wexwex'">wexwex</TYPO> 
        // <TYPO descr="Typo: In word 'wexWex'">wexWex</TYPO> 
        // <TYPO descr="Typo: In word 'Wexwex'">Wexwex</TYPO> 
        // <TYPO descr="Typo: In word 'WEXWEX'">WEXWEX</TYPO>
        // WexWex
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test mixed words can be added via SaveTo action`() {
    assertTrue(GrazieSpellCheckerEngine.getInstance(grazie.project).getSpeller()!!.isMisspelled("typppoStatusChanged"))
    grazie.fixture.configureByText("a.java", "// <TYPO descr=\"Typo: In word 'typppoStatusChanged'\">typppoStatusChanged</TYPO>")
    grazie.fixture.checkHighlighting()

    grazie.saveWord("typppoStatusChanged")
    grazie.fixture.configureByText("a.java", "// typppoStatusChanged")
    grazie.fixture.checkHighlighting()
  }

  @PerformanceUnitTest
  @Test
  fun `test performance on typos by word-level spellchecker`() {
    // German is not enabled on purpose to disable suggestion-based typo detection
    Benchmark.newBenchmark("word-level spellchecking performance") {
      grazie.runHighlightTestForFile("ide/language/java/Dictionary.java")
    }.setup {
      grazie.dropPsiCaches()
      GrazieSpellCheckerEngine.getInstance(grazie.project).dropSuggestionCache()
    }.start()
  }

  @Test
  fun `test todo in dumb mode`() {
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, grazie.testRootDisposable)
    runInDumbModeSynchronously(grazie.project) {
      grazie.fixture.configureByText("a.java", "// TODO It is an friend of human")
      grazie.fixture.checkHighlighting()
    }
  }

  @Test
  fun `test false positive an with consonant`() {
    grazie.fixture.configureByText("a.java", """
      // Returns an xlsx file based on given type. I have an mp3.
      // Writes a uint32_t to a buffer.
      // It is an SA disk. It is an SC disk. It is a SATA disk.
      // It is a SCORN with no grade. It is a SCORM with no grade.
      // It is an ECO summit. It is an ECS summit.
      // It is an ISS mission. It is an ISSA mission.
      // Sending an Sms. Sending an sms. Sending an SMS.
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test mass apply is available around problems`() {
    // Action is available because there is a typo intersecting with selection
    grazie.fixture.configureByText("a.java", """
      class A {
        void foo() {
          // <TYPO descr="Typo: In word 'tagret'">t<caret><selection>a</selection>gret</TYPO>
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    assertNotNull(grazie.fixture.getAvailableIntention("Accept all writing suggestions…"))

    // Action is available because there is caret near typo
    grazie.fixture.configureByText("b.java", """
      class B {
        void foo() {
          // <TYPO descr="Typo: In word 'tagret'">tagret</TYPO>
          // <caret>target
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    assertNotNull(grazie.fixture.getAvailableIntention("Accept all writing suggestions…"))

    // Action is not available because there is no grammar / style / spelling issues in selection
    grazie.fixture.configureByText("c.java", """
      class C {
        void foo() {
          // <TYPO descr="Typo: In word 'tagret'">tagret</TYPO>
          // <selection>tar</selection>get
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    assertNull(grazie.fixture.getAvailableIntention("Accept all writing suggestions…"))
  }

  @Test
  fun `test mass apply is not available if there are no suggestions`() {
    GrazieConfig.update { it.withDomainEnabledRules(TextStyleDomain.CodeComment, setOf("LanguageTool.EN.IT_IS_OBVIOUS")) }
    grazie.fixture.configureByText("a.java", """
      class A {
        void foo() {
          // I think <STYLE_SUGGESTION descr="IT_IS_OBVIOUS">it's pretty obvious</STYLE_SUGGESTION> that he likes you.
        }
      }
    """)
    grazie.fixture.checkHighlighting()
    assertNull(grazie.fixture.getAvailableIntention("Accept all writing suggestions…"))
  }

  @Test
  fun `test dependency parser creates correct trees based on text content language`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.AMERICAN_ENGLISH))
    grazie.fixture.configureByText("a.java", """
      // In der tiefen Winternacht saß der alte Mann am Feuer. Die fast.
      
      // Hello. This is really English text. Nothing to see here. Die fast.
    """.trimIndent())
    assertDoesNotThrow {
      grazie.fixture.checkHighlighting()
    }
  }

  @Test
  fun `test asian-english mixed texts`() {
    grazie.runHighlightTestForFile("ide/language/java/Mixed.java")

    grazie.enableProofreadingFor(setOf(Lang.JAPANESE))
    grazie.runHighlightTestForFile("ide/language/java/Mixed.java")
  }

  @Test
  fun `test spellchecking does not produce NoSuchMethodError`() {
    grazie.fixture.configureByText("a.java", """
     // A little bit more text to sound like English. 
     // It's a <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.MISPLACED_SPACE">grea tbig</GRAMMAR_ERROR> adventure.
    """.trimIndent())
    assertDoesNotThrow {
      grazie.fixture.checkHighlighting()
    }
  }

  private fun doTest(beforeText: String, afterText: String, hint: String) {
    grazie.fixture.configureByText("a.java", beforeText)
    val intentionAction = grazie.fixture.findSingleIntention(hint)
    assertNotNull(intentionAction)
    grazie.fixture.launchAction(intentionAction)
    grazie.fixture.checkResult(afterText)
  }
}
