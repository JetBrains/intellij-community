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

  private fun runRelaxedChecks(
    middle: String,
    completion: String,
    expected: Map<RelaxedSimilarityUtils.RelaxedMetric, RelaxedSimilarityUtils.RelaxedResult>,
    prefix: String = "",
    suffix: String = "",
    stripChars: Boolean = false,
  ) = expected.forEach { check, result ->
    assertEquals(result, check.compute(middle, completion, prefix, suffix, stripChars)) { "For ${check.javaClass.simpleName}" }
  }

  @Test
  fun `test computeRelaxedExactMatch with exact match one line`() = runRelaxedChecks(
    middle = "hello",
    completion = "hello",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
    ),
  )

  @Test
  fun `test computeRelaxedExactMatch with exact match multiple lines`() = runRelaxedChecks(
    middle = "hello\nworld",
    completion = "hello\nworld",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
    ),
  )

  @Test
  fun `test computeRelaxedExactMatch with exact match multiple lines but in different order`() = runRelaxedChecks(
    middle = "hello\nworld",
    completion = "world\nhello",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
    ),
  )

  @Test
  fun `test computeRelaxedExactMatch with partial match in first line`() = runRelaxedChecks(
    middle = "llo\nworld",
    completion = "llo",
    prefix = "he",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.MULTI,
    ),
  )

  @Test
  fun `test computeRelaxedExactMatch with partial match in last line`() = runRelaxedChecks(
    middle = "hello\nworld",
    completion = "world\nbye",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.SINGLE,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.SINGLE,
    ),
  )

  @Test
  fun `test computeRelaxedExactMatch no match`() = runRelaxedChecks(
    prefix = "hello\n",
    middle = "world\n",
    completion = "kotlin\n",
    suffix = "\nbye\n",
    expected = mapOf(
      RelaxedSimilarityUtils.RelaxedExactMatch() to RelaxedSimilarityUtils.RelaxedResult.NO,
      RelaxedSimilarityUtils.RelaxedEditDistance() to RelaxedSimilarityUtils.RelaxedResult.NO,
    ),
  )
}