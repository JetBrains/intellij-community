package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InternalApiRecallTest {

  @Test
  fun `extractPredictedApiCallsFromLookup should return list of predicted API calls when present`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = mapOf("predicted_api_calls" to "call1\ncall2\ncall3")
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractPredictedApiCallsFromLookup(lookup)

    assertEquals(listOf("call1", "call2", "call3"), result)
  }

  @Test
  fun `extractPredictedApiCallsFromLookup should return empty list when predicted API calls are absent`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = emptyMap()
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractPredictedApiCallsFromLookup(lookup)

    assertEquals(emptyList<String>(), result)
  }

  @Test
  fun `extractPredictedApiCallsFromLookup should return empty list when predicted API calls are empty`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = mapOf("predicted_api_calls" to "")
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractPredictedApiCallsFromLookup(lookup)

    assertEquals(emptyList<String>(), result)
  }

  @Test
  fun `extractExpectedApiCallsFromLookup should return list of expected API calls when present`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = mapOf("ground_truth_internal_api_calls" to "call1\ncall2\ncall3")
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractExpectedApiCallsFromLookup(lookup)

    assertEquals(listOf("call1", "call2", "call3"), result)
  }

  @Test
  fun `extractExpectedApiCallsFromLookup should return empty list when expected API calls are absent`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = emptyMap()
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractExpectedApiCallsFromLookup(lookup)

    assertEquals(emptyList<String>(), result)
  }

  @Test
  fun `extractExpectedApiCallsFromLookup should return empty list when expected API calls are empty`() {
    val lookup = Lookup(
      prefix = "test",
      offset = 0,
      suggestions = listOf(),
      latency = 10L,
      isNew = false,
      additionalInfo = mapOf("ground_truth_internal_api_calls" to "")
    )
    val apiRecall = InternalApiRecall()

    val result = apiRecall.extractExpectedApiCallsFromLookup(lookup)

    assertEquals(emptyList<String>(), result)
  }
}