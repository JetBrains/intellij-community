package com.intellij.grazie.hunspell

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance

class HunspellGermanTest : GrazieTestBase() {

  override val enableGrazieChecker: Boolean = true

  private fun doTestSuggestion(word: String, suggestion: String) {
    val manager = getInstance(project)
    assertTrue("'$word' expected to have a spelling mistake", manager.hasProblem(word))
    val suggestions = manager.getSuggestions(word)
    assertTrue("$suggestion expected to be in the list of suggestions: $suggestions", suggestions.contains(suggestion))
  }

  fun `test hunspell de`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    runHighlightTestForFile("hunspell/Hunspell.java")
  }

  fun `test swiss german suggestions`() {
    enableProofreadingFor(setOf(Lang.SWISS_GERMAN))
    doTestSuggestion("anschließend", "anschliessend")
  }

  fun `test standard german suggestions`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    doTestSuggestion("anschliessend", "anschließend")
  }
}