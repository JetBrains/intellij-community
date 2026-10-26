package com.intellij.grazie.hunspell

import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class HunspellGermanTest : HunspellTest() {

  @Test
  fun `test hunspell de`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    grazie.runHighlightTestForFile("hunspell/Hunspell.java")
  }

  @Test
  fun `test swiss german suggestions`() {
    grazie.enableProofreadingFor(setOf(Lang.SWISS_GERMAN))
    assertSuggestion("anschließend", "anschliessend")
  }

  @Test
  fun `test standard german suggestions`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    assertSuggestion("anschliessend", "anschließend")
  }
}