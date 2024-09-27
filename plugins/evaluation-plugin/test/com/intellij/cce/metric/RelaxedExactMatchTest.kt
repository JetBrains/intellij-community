package com.intellij.cce.metric

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RelaxedExactMatchTest {
  @Test
  fun `test line pre-processing`() {
    val processed = RelaxedSimilarityUtils.preProcessLines(
      completion = "turn main\ndef fun",
      prefix = "hi\nre",
      suffix = "ction()::\nendfun",
      stripChars = false,
    )
    Assertions.assertEquals(listOf("return main", "def function()::"), processed)
  }
}