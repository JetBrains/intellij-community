package com.intellij.grazie.hunspell

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
internal class HunspellEnglishTest: HunspellTest() {

  @Test
  fun `test no profanities`() {
    assertNoSuggestions("onoffswitch", "sonsofbitches")
  }
}
