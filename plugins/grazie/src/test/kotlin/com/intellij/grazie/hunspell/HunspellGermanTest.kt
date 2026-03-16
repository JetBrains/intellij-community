package com.intellij.grazie.hunspell

import com.intellij.grazie.jlanguage.Lang

class HunspellGermanTest : HunspellTest() {

  fun `test hunspell de`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    runHighlightTestForFile("hunspell/Hunspell.java")
  }

  fun `test swiss german suggestions`() {
    enableProofreadingFor(setOf(Lang.SWISS_GERMAN))
    assertSuggestion("anschließend", "anschliessend")
  }

  fun `test standard german suggestions`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    assertSuggestion("anschliessend", "anschließend")
  }
}