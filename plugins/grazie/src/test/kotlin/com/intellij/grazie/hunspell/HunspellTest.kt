package com.intellij.grazie.hunspell

import com.intellij.grazie.grazieTestFixture
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

abstract class HunspellTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  protected val grazie by grazieTestFixture(projectFixture)

  fun assertSuggestion(word: String, suggestion: String) {
    val manager = getInstance(grazie.project)
    assertTrue(manager.hasProblem(word), "'$word' expected to have a spelling mistake")
    val suggestions = manager.getSuggestions(word)
    assertTrue(suggestions.contains(suggestion), "$suggestion is expected to be in the list of suggestions: $suggestions")
  }

  fun assertNoSuggestions(word: String, suggestion: String) {
    val manager = getInstance(grazie.project)
    assertTrue(manager.hasProblem(word), "'$word' expected to have a spelling mistake")
    val suggestions = manager.getSuggestions(word)
    assertFalse(suggestions.contains(suggestion), "$suggestion isn't expected to be in the list of suggestions: $suggestions")
  }
}
