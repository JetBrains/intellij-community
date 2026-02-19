package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present

object GrazieSpellchecker {
  fun lookup(word: String): Dictionary.LookupStatus = service<GrazieCheckers>().lookup(word)

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): Collection<String> {
    return service<GrazieCheckers>().getSuggestions(word)
  }
}

class SpellCheckerTest : GrazieTestBase() {

  private fun doSuggestionTest(word: String, expected: String) {
    assertTrue(getInstance(project).hasProblem(word))
    val suggestions = getInstance(project).getSuggestions(word)
    assertTrue(suggestions.isNotEmpty())
    assertEquals(expected, suggestions.first())
  }

  fun `test empty word`() {
    assertFalse(getInstance(project).hasProblem(""))
  }

  fun `test emoji`() {
    assertFalse(getInstance(project).hasProblem("\uD83D\uDE4B\uD83C\uDFFF"))
  }

  fun `test alien word`() {
    assertEquals(Alien, GrazieSpellchecker.lookup("例子"))
  }

  fun `test unknown word`() {
    val word = "dasfhaljkwehfjhadfdsafdsv"
    assertTrue(GrazieSpellchecker.lookup(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).isEmpty())
  }

  fun `test correct word`() {
    assertFalse(getInstance(project).hasProblem("banana"))
  }

  fun `test incorrect word`() {
    val word = "bannana"
    assertTrue(getInstance(project).hasProblem(word))
    assertTrue(getInstance(project).getSuggestions(word).contains("banana"))
  }

  fun `test correct word with apostrophe`() {
    enableProofreadingFor(setOf(Lang.ITALIAN))
    assertEquals(Present, GrazieSpellchecker.lookup("un'espressione"))
  }

  fun `test incorrect word with apostrophe`() {
    enableProofreadingFor(setOf(Lang.ITALIAN))
    val word = "un'espresssione"
    assertTrue(GrazieSpellchecker.lookup(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("un'espressione"))
  }

  fun `test speller rules are disabled when hunspell is enabled`() {
    enableProofreadingFor(setOf(Lang.RUSSIAN, Lang.UKRAINIAN, Lang.GERMANY_GERMAN))
    // Even though words shouldn't be treated as alien because corresponding languages have been enabled,
    // `lookup` will return `Alien` anyway.
    // It happens because LT Spelling Tools are disabled if Hunspell dictionary is enabled
    assertEquals(Alien, GrazieSpellchecker.lookup("привет"))
    assertEquals(Alien, GrazieSpellchecker.lookup("entschuldigung"))
    assertEquals(Alien, GrazieSpellchecker.lookup("кiт"))
  }

  fun `test advanced dat suggestions`() {
    enableProofreadingFor(setOf(Lang.RUSSIAN))
    doSuggestionTest("Врядтли", "Вряд ли")
    doSuggestionTest("Грейзи", "Грацие")
  }
}
