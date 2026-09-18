package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager

class RuleIntentionTest : GrazieTestBase() {

  @Suppress("NonAsciiCharacters")
  fun `test batch quick fix`() {
    myFixture.configureByText("a.txt", """
      I <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.LATIN_CYRILLIC_CONFUSION"><caret>hаve</GRAMMAR_ERROR> a cat,
      and you <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.LATIN_CYRILLIC_CONFUSION">hаve</GRAMMAR_ERROR> a dog.
    """.trimIndent())
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Apply this suggestion everywhere in the file"))

    myFixture.checkResult("""
      I have a cat,
      and you have a dog.
    """.trimIndent())
  }

  fun `test change language variant fix`() {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    myFixture.configureByText("a.txt", """
      The <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES"><caret>colour</STYLE_SUGGESTION> of the sky is blue.
    """.trimIndent())
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to British English"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertEmpty(myFixture.doHighlighting())

    undo()
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
  }

  fun `test use Oxford spelling fix from US`() {
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    myFixture.configureByText("a.txt", """
      <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Analysing</STYLE_SUGGESTION> language is great!
    """.trimIndent())
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)
    assertEmpty(myFixture.doHighlighting())

    undo()
    assertEquals(setOf(Lang.AMERICAN_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertFalse(GrazieConfig.get().useOxfordSpelling)
  }

  fun `test use Oxford spelling fix from GB`() {
    GrazieConfig.update { it.copy(enabledLanguages = setOf(Lang.BRITISH_ENGLISH)) }
    assertFalse(GrazieConfig.get().useOxfordSpelling)

    myFixture.configureByText("a.txt", """
      <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">Summarizing</STYLE_SUGGESTION> a text is great!
    """.trimIndent())
    myFixture.checkHighlighting()

    myFixture.launchAction(myFixture.findSingleIntention("Switch to Oxford spelling (UK)"))
    assertEquals(setOf(Lang.BRITISH_ENGLISH), GrazieConfig.get().enabledLanguages)
    assertTrue(GrazieConfig.get().useOxfordSpelling)
    assertEmpty(myFixture.doHighlighting())
  }

  private fun undo() {
    UndoManager.getInstance(project).undo(FileEditorManager.getInstance(project).getSelectedEditor(myFixture.file.virtualFile))
  }
}
