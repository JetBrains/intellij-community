package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present

object GrazieSpellchecker {
  fun isCorrect(word: String): Dictionary.LookupStatus = service<GrazieCheckers>().lookup(word)

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): Collection<String> {
    return service<GrazieCheckers>().getSuggestions(word)
  }
}

class SpellCheckerTest : GrazieTestBase() {
  fun `test empty word`() {
    assertEquals(Present, GrazieSpellchecker.isCorrect(""))
  }

  fun `test emoji`() {
    assertEquals(Present, GrazieSpellchecker.isCorrect("\uD83D\uDE4B\uD83C\uDFFF"))
  }

  fun `test alien word`() {
    assertEquals(Alien, GrazieSpellchecker.isCorrect("例子"))
  }

  fun `test unknown word`() {
    val word = "dasfhaljkwehfjhadfdsafdsv"
    assertTrue(GrazieSpellchecker.isCorrect(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).isEmpty())
  }

  fun `test correct word`() {
    assertEquals(Present, GrazieSpellchecker.isCorrect("banana"))
  }

  fun `test incorrect word`() {
    val word = "bannana"
    assertTrue(GrazieSpellchecker.isCorrect(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("banana"))
  }

  fun `test correct word with apostrophe`() {
    assertEquals(Present, GrazieSpellchecker.isCorrect("un'espressione"))
  }

  fun `test incorrect word with apostrophe`() {
    val word = "un'espresssione"
    assertTrue(GrazieSpellchecker.isCorrect(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("un'espressione"))
  }
}
