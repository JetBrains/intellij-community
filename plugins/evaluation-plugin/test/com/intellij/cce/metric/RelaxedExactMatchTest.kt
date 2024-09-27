package com.intellij.cce.metric

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RelaxedExactMatchTest {
  private data class ProcessingTestCase(
    val prefix: String,
    val completion: String,
    val suffix: String,
    val strip: Boolean,
  )
  private val processingCases = listOf(
    ProcessingTestCase(
      prefix = "hi\nre",
      completion = "turn main\ndef fun",
      suffix = "ction()::\nendfun",
      strip = false,
    ) to listOf("return main", "def function()::"),
    ProcessingTestCase(
      prefix = "class MyClas",
      completion = "s:\ndef __init__(self):\npass\n",
      suffix = "\n    def method(self):\n        pass\n",
      strip = false,
    ) to listOf("class MyClass:", "def __init__(self):", "pass"),
    ProcessingTestCase(
      prefix = "class MyClas",
      completion = "s:\ndef __init__(self):\npass\n",
      suffix = "",
      strip = true,
    ) to listOf("classMyClass", "def__init__self", "pass"),
  )

  private fun ProcessingTestCase.process(): List<String> = RelaxedSimilarityUtils.preProcessLines(
    completion = completion,
    prefix = prefix,
    suffix = suffix,
    stripChars = strip,
  )

  @Test
  fun `test line pre-processing`() = processingCases.forEach {
    (inputs, outputs) -> Assertions.assertEquals(outputs, inputs.process())
  }
}