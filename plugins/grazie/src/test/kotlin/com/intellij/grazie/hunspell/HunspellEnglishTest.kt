package com.intellij.grazie.hunspell

internal class HunspellEnglishTest: HunspellTest() {

  fun `test no profanities`() {
    assertNoSuggestions("onoffswitch", "sonsofbitches")
  }
}
