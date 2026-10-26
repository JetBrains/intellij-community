package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class RuleIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Suppress("NonAsciiCharacters")
  @Test
  fun `test batch quick fix`() {
    grazie.fixture.configureByText("a.txt", """
      I <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.LATIN_CYRILLIC_CONFUSION"><caret>hаve</GRAMMAR_ERROR> a cat,
      and you <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.LATIN_CYRILLIC_CONFUSION">hаve</GRAMMAR_ERROR> a dog.
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("Apply this suggestion everywhere in the file"))

    grazie.fixture.checkResult("""
      I have a cat,
      and you have a dog.
    """.trimIndent())
  }

  @Test
  fun `test change language variant fix`(): Unit = timeoutRunBlocking {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    grazie.fixture.configureByText("a.txt", """
      The <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES"><caret>colour</STYLE_SUGGESTION> of the sky is blue.
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("Switch to British English"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(grazie.fixture.doHighlighting().isEmpty())

    undo()
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
  }

  @Test
  fun `test use Oxford spelling fix from US`(): Unit = timeoutRunBlocking {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    grazie.fixture.configureByText("a.txt", """
      <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Analysing</STYLE_SUGGESTION> language is great!
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)
    assertTrue(grazie.fixture.doHighlighting().isEmpty())

    undo()
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)
  }

  @Test
  fun `test use Oxford spelling fix from GB`() {
    GrazieConfig.update { it.copy(enabledLanguages = setOf(Lang.BRITISH_ENGLISH)) }
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    grazie.fixture.configureByText("a.txt", """
      <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Summarizing</STYLE_SUGGESTION> a text is great!
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    grazie.fixture.launchAction(grazie.fixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)
    assertTrue(grazie.fixture.doHighlighting().isEmpty())
  }

  private suspend fun undo() {
    withContext(Dispatchers.EDT) {
      UndoManager.getInstance(grazie.project).undo(FileEditorManager.getInstance(grazie.project).getSelectedEditor(grazie.fixture.file.virtualFile))
    }
  }
}
