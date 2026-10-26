package com.intellij.grazie.grammar

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

object GrazieSpellchecker {
  fun lookup(word: String): Dictionary.LookupStatus = ProgressManager.getInstance().runProcess(
    Computable {
      service<GrazieCheckers>().lookup(word)
    }, ProgressIndicatorBase())

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): Collection<String> {
    return service<GrazieCheckers>().getSuggestions(word)
  }
}

@TestApplication
class SpellCheckerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  private fun doSuggestionTest(word: String, expected: String) {
    assertTrue(getInstance(grazie.project).hasProblem(word))
    val suggestions = getInstance(grazie.project).getSuggestions(word)
    assertTrue(suggestions.isNotEmpty())
    assertEquals(expected, suggestions.first())
  }

  @Test
  fun `test empty word`() {
    assertFalse(getInstance(grazie.project).hasProblem(""))
  }

  @Test
  fun `test emoji`() {
    assertFalse(getInstance(grazie.project).hasProblem("\uD83D\uDE4B\uD83C\uDFFF"))
  }

  @Test
  fun `test alien word`() {
    assertEquals(Alien, GrazieSpellchecker.lookup("例子"))
  }

  @Test
  fun `test unknown word`() {
    val word = "dasfhaljkwehfjhadfdsafdsv"
    assertTrue(GrazieSpellchecker.lookup(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).isEmpty())
  }

  @Test
  fun `test correct word`() {
    assertFalse(getInstance(grazie.project).hasProblem("banana"))
  }

  @Test
  fun `test incorrect word`() {
    val word = "bannana"
    assertTrue(getInstance(grazie.project).hasProblem(word))
    assertTrue(getInstance(grazie.project).getSuggestions(word).contains("banana"))
  }

  @Test
  fun `test correct word with apostrophe`() {
    grazie.enableProofreadingFor(setOf(Lang.ITALIAN))
    assertEquals(Present, GrazieSpellchecker.lookup("un'espressione"))
  }

  @Test
  fun `test incorrect word with apostrophe`() {
    grazie.enableProofreadingFor(setOf(Lang.ITALIAN))
    val word = "un'espresssione"
    assertTrue(GrazieSpellchecker.lookup(word).isNotPresent)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("un'espressione"))
  }

  @Test
  fun `test speller rules are disabled when hunspell is enabled`() {
    grazie.enableProofreadingFor(setOf(Lang.RUSSIAN, Lang.UKRAINIAN, Lang.GERMANY_GERMAN))
    // Even though words shouldn't be treated as alien because corresponding languages have been enabled,
    // `lookup` will return `Alien` anyway.
    // It happens because LT Spelling Tools are disabled if Hunspell dictionary is enabled
    assertEquals(Alien, GrazieSpellchecker.lookup("привет"))
    assertEquals(Alien, GrazieSpellchecker.lookup("entschuldigung"))
    assertEquals(Alien, GrazieSpellchecker.lookup("кiт"))
  }

  @Test
  fun `test advanced dat suggestions`() {
    grazie.enableProofreadingFor(setOf(Lang.RUSSIAN))
    doSuggestionTest("Врядтли", "Вряд ли")
    doSuggestionTest("Грейзи", "Грацие")
  }

  @Test
  fun `test unavailable enabled language is ignored`() {
    grazie.enableProofreadingFor(setOf(Lang.ARABIC))
    assertDoesNotThrow {
      GrazieSpellchecker.lookup("مرحبا")
    }
  }
}
