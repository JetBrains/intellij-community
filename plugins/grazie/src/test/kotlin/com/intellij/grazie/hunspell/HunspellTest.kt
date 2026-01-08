package com.intellij.grazie.hunspell

import com.intellij.grazie.GrazieTestBase
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance

abstract class HunspellTest : GrazieTestBase() {

  fun assertSuggestion(word: String, suggestion: String) {
    val manager = getInstance(project)
    assertTrue("'$word' expected to have a spelling mistake", manager.hasProblem(word))
    val suggestions = manager.getSuggestions(word)
    assertTrue("$suggestion is expected to be in the list of suggestions: $suggestions", suggestions.contains(suggestion))
  }

  fun assertNoSuggestions(word: String, suggestion: String) {
    val manager = getInstance(project)
    assertTrue("'$word' expected to have a spelling mistake", manager.hasProblem(word))
    val suggestions = manager.getSuggestions(word)
    assertFalse("$suggestion isn't expected to be in the list of suggestions: $suggestions", suggestions.contains(suggestion))
  }
}
