package com.intellij.grazie.pro

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.TextProblemSeverities
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class RuleIntentionTest : BaseTestCase() {

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieInspection::class.java, GrazieInspection.Grammar::class.java, GrazieInspection.Style::class.java)
  }

  @NeedsCloud
  @Test
  fun `test rule quick fix presentation`() {
    myFixture.configureByText("a.txt", "Hello. <caret>A water is very ungrammatical.")
    myFixture.doHighlighting()
    val intentionTexts = availableIntentions.map { it.text }
    assertEquals(
      listOf("Uncountable 'water' shouldn’t have indefinite articles", "Water"),
      intentionTexts.subList(0, 2))
    assertTrue(intentionTexts.any { it.startsWith("Configure rule 'Incorrect article usage'") }) { intentionTexts.toString() }

    myFixture.configureByText("a.txt", "The dogs always <caret>walks.")
    myFixture.doHighlighting()
    assertEquals(listOf("dogs always walk", "dog always walks"), availableIntentions.map { it.text }.subList(1, 3))

    myFixture.configureByText("a.txt", "<caret>Margaret who hated trains didn’t travel by rail at all.")
    myFixture.doHighlighting()
    assertEquals("Margaret,…trains,", availableIntentions[1].text)
  }

  @NeedsCloud
  @Test
  fun `test newline in the quick fix`() {
    HighlightingTest.enableLanguages(setOf(Lang.GERMANY_GERMAN), project, testRootDisposable)
    myFixture.configureByText("a.md", "Beste Weihnachtsg<caret>rüße, Alex")
    myFixture.doHighlighting()
    val intentionTexts = availableIntentions.map { it.text }
    assertEquals(
      listOf(
        "Verwenden Sie kein Satzzeichen nach der Schlussformel",
        "Beste Weihnachtsgrüße⏎"
      ),
      intentionTexts.subList(0, 2))
  }

  @NeedsCloud
  @Test
  fun `test rephrase as a quick fix for lemma repetition warnings`() {
    myFixture.configureByText("a.txt", "I said yes. He said no. Then I <caret>said maybe.")
    myFixture.doHighlighting()
    val intentionTexts = availableIntentions.map { it.text }
    assertEquals("Rephrase", intentionTexts[0], intentionTexts.toString())
  }

  @NeedsCloud
  @Test
  fun `test batch quick fix`() {
    myFixture.configureByText("a.md", """
      There is some range (<caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Typography.HYPHEN_IN_RANGES">1 - 2</STYLE_SUGGESTION>),
      and then there is another range (<STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Typography.HYPHEN_IN_RANGES">2 - 3</STYLE_SUGGESTION>), both being very fine.
      """.trimIndent())

    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.availableIntentions.find { it.text == "Apply this suggestion everywhere in the file" }!!)

    myFixture.checkResult("""
      There is some range (1–2),
      and then there is another range (2–3), both being very fine.
    """.trimIndent())
  }

  @NeedsCloud
  @Test
  fun `test rephrase action is available and first`() {
    HighlightingTest.enableRules("Grazie.RuleEngine.En.Style.LEMMA_REPETITION")
    myFixture.configureByText("a.txt",
      """
        I said yes. He said no.
        Then I <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.LEMMA_REPETITION"><caret>said</STYLE_SUGGESTION> OK.
      """.trimIndent()
    )
    myFixture.checkHighlighting()
    assertEquals("Rephrase", myFixture.availableIntentions[0].text)
  }

  @NeedsCloud
  @Test
  fun `test change language variant fix`() {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    myFixture.configureByText("a.txt",
      """
        This is the question, <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.EG_IE_PUNCTUATION"><caret>i.e.</STYLE_SUGGESTION> something I want to ask from you.
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to British English"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)

    assertNull(myFixture.doHighlighting().find { it.severity.name == "GRAMMAR_ERROR" })

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        UndoManager.getInstance(project).undo(FileEditorManager.getInstance(project).selectedEditor)
      }
    }
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
  }

  @NeedsCloud
  @Test
  fun `test use Oxford spelling fix from US`() {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    myFixture.configureByText("a.txt",
      """
        <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Analysing</STYLE_SUGGESTION> language is great!
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)

    assertEmpty(myFixture.doHighlighting())

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        UndoManager.getInstance(project).undo(FileEditorManager.getInstance(project).selectedEditor)
      }
    }
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)
  }

  @NeedsCloud
  @Test
  fun `test use Oxford spelling fix from GB`() {
    HighlightingTest.enableLanguages(setOf(Lang.BRITISH_ENGLISH), project, testRootDisposable)
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    myFixture.configureByText("a.txt",
      """
        <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Summarizing</STYLE_SUGGESTION> a text is great!
      """.trimIndent()
    )
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)

    assertEmpty(myFixture.doHighlighting())
  }

  @NeedsCloud
  @Test
  fun `test change parameter quick fix`() {
    myFixture.configureByText("a.txt", "I <caret>believe in Santa anymore.")
    myFixture.doHighlighting()
    assertOrderedEquals(
      myFixture.availableIntentions.map { it.text }.subList(0, 3),
      "'anymore' implies a negative phrase, condition, or question",
      "don't believe",
      "Configure contraction settings"
    )
  }

  @NeedsCloud
  @Test
  fun `test suppressing grammar error issues`() {
    myFixture.configureByText("a.md", """
      This is the first sentence. <caret><GRAMMAR_ERROR descr="IN_ON_WEEKDAY">In</GRAMMAR_ERROR> Tuesday, we'll have a meeting.
    """.trimIndent())
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("Ignore 'In Tuesday, we'"))
    myFixture.launchAction(myFixture.findSingleIntention("Ignore 'In' in this sentence"))
    assertEmpty(myFixture.doHighlighting(TextProblemSeverities.GRAMMAR_ERROR))

    // Verify that changing the first sentence doesn't have any effect on the following one
    myFixture.configureByText("a.md", """
      This is not the first sentence. <caret><GRAMMAR_ERROR descr="IN_ON_WEEKDAY">In</GRAMMAR_ERROR> Tuesday, we'll have a meeting.
    """.trimIndent())
    assertEmpty(myFixture.doHighlighting(TextProblemSeverities.GRAMMAR_ERROR))
  }
}