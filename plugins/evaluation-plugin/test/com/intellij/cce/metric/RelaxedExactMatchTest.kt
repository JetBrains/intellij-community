package com.intellij.cce.metric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelaxedExactMatchTest {
  private data class Sample(
    val prefix: String,
    val middle: String,
    val suffix: String,
    val strip: Boolean = false,
  )

  private val processingCases = listOf(
    Sample(
      prefix = "hi\nre",
      middle = "turn main\ndef fun",
      suffix = "ction()::\nendfun",
      strip = false,
    ) to listOf("return main", "def function()::"),
    Sample(
      prefix = "class MyClas",
      middle = "s:\ndef __init__(self):\npass\n",
      suffix = "\n    def method(self):\n        pass\n",
      strip = false,
    ) to listOf("class MyClass:", "def __init__(self):", "pass"),
    Sample(
      prefix = "class MyClas",
      middle = "s:\ndef __init__(self):\npass\n",
      suffix = "",
      strip = true,
    ) to listOf("classMyClass", "def__init__self", "pass"),
  )

  private fun Sample.process(): List<String> = RelaxedSimilarityUtils.preProcessLines(
    completion = middle,
    prefix = prefix,
    suffix = suffix,
    stripChars = strip,
  )

  @Test
  fun `test line pre-processing`() = processingCases.forEach {
    (inputs, outputs) -> assertEquals(outputs, inputs.process())
  }

  private fun runRelaxedExactMatch(
    middle: String,
    completion: String,
    expected: RelaxedSimilarityUtils.RelaxedResult,
    prefix: String = "",
    suffix: String = "",
    stripChars: Boolean = false,
  ) = assertEquals(expected, RelaxedSimilarityUtils.computeRelaxedExactMatch(
    middle = middle,
    completion = completion,
    prefix = prefix,
    suffix = suffix,
    stripChars = stripChars,
  ))

  @Test
  fun `test computeRelaxedExactMatch with exact match one line`() = runRelaxedExactMatch(
    middle = "hello",
    completion = "hello",
    expected = RelaxedSimilarityUtils.RelaxedResult.MULTI,
  )

  @Test
  fun `test computeRelaxedExactMatch with exact match multiple lines`() = runRelaxedExactMatch(
    middle = "hello\nworld",
    completion = "hello\nworld",
    expected = RelaxedSimilarityUtils.RelaxedResult.MULTI,
  )

  @Test
  fun `test computeRelaxedExactMatch with exact match multiple lines but in different order`() = runRelaxedExactMatch(
    middle = "hello\nworld",
    completion = "world\nhello",
    expected = RelaxedSimilarityUtils.RelaxedResult.MULTI,
  )

  @Test
  fun `test computeRelaxedExactMatch with partial match in first line`() = runRelaxedExactMatch(
    middle = "llo\nworld",
    completion = "llo",
    prefix = "he",
    expected = RelaxedSimilarityUtils.RelaxedResult.MULTI,
  )

  @Test
  fun `test computeRelaxedExactMatch with partial match in last line`() = runRelaxedExactMatch(
    middle = "hello\nworld",
    completion = "world\nbye",
    expected = RelaxedSimilarityUtils.RelaxedResult.ANY,
  )

  @Test
  fun `test computeRelaxedExactMatch no match`() = runRelaxedExactMatch(
    prefix = "hello\n",
    middle = "world\n",
    completion = "kotlin\n",
    suffix = "\nbye\n",
    expected = RelaxedSimilarityUtils.RelaxedResult.NO,
  )
}