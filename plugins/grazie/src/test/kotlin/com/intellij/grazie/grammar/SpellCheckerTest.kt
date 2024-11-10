package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import org.junit.Test

object GrazieSpellchecker {
  fun isCorrect(word: String): Boolean? {
    return service<GrazieCheckers>().isCorrect(word)
  }

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): Collection<String> {
    return service<GrazieCheckers>().getSuggestions(word)
  }
}

class SpellCheckerTest : GrazieTestBase() {
  @Test
  fun `test empty word`() {
    assertTrue(GrazieSpellchecker.isCorrect("") ?: false)
  }

  @Test
  fun `test emoji`() {
    assertTrue(GrazieSpellchecker.isCorrect("\uD83D\uDE4B\uD83C\uDFFF") ?: false)
  }

  @Test
  fun `test alien word`() {
    assertNull(GrazieSpellchecker.isCorrect("例子"))
  }

  @Test
  fun `test unknown word`() {
    val word = "dasfhaljkwehfjhadfdsafdsv"
    assertFalse(GrazieSpellchecker.isCorrect(word) ?: true)
    assertTrue(GrazieSpellchecker.getSuggestions(word).isEmpty())
  }

  @Test
  fun `test correct word`() {
    assertTrue(GrazieSpellchecker.isCorrect("banana") ?: false)
  }

  @Test
  fun `test incorrect word`() {
    val word = "bannana"
    assertFalse(GrazieSpellchecker.isCorrect(word) ?: true)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("banana"))
  }

  @Test
  fun `test correct word with apostrophe`() {
    assertTrue(GrazieSpellchecker.isCorrect("un'espressione") ?: false)
  }

  @Test
  fun `test incorrect word with apostrophe`() {
    val word = "un'espresssione"
    assertFalse(GrazieSpellchecker.isCorrect(word) ?: true)
    assertTrue(GrazieSpellchecker.getSuggestions(word).contains("un'espressione"))
  }
}
