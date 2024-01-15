// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MetricsEvaluatorTest {
  companion object {
    private val sessionTop1 = Mockito.mock(Session::class.java)
    private val sessionTop3 = Mockito.mock(Session::class.java)
    private val sessionTop5 = Mockito.mock(Session::class.java)
    private val sessionNone = Mockito.mock(Session::class.java)

    val sessionTop1Custom = Mockito.mock(Session::class.java)
    val sessionNoTop1Custom = Mockito.mock(Session::class.java)
    val sessionNoMatchCustom = Mockito.mock(Session::class.java)
    val sessionNoSuggestionsCustom = Mockito.mock(Session::class.java)

    private const val EXPECTED = "expected"
    private const val UNEXPECTED = "unexpected"
  }

  init {
    mockSession(sessionTop1, expected = 0, total = 5)
    mockSession(sessionTop3, expected = 2, total = 5)
    mockSession(sessionTop5, expected = 4, total = 5)
    mockSession(sessionNone, expected = null, total = 10)
    mockSessionCustom(
      sessionTop1Custom,
      "expected string_",
      listOf("expected", "expected string_", "qwerty")
    )
    mockSessionCustom(
      sessionNoTop1Custom,
      "expected string_",
      listOf("expected other", "expected string_", "qwerty")
    )
    mockSessionCustom(
      sessionNoMatchCustom,
      "expected string",
      listOf("suggestion_1", "suggestion_2", "suggestion_3")
    )
    mockSessionCustom(
      sessionNoSuggestionsCustom,
      "expected string",
      listOf()
    )
  }

  @Test
  fun `test metrics evaluator`() {
    val evaluator = MetricsEvaluator.withDefaultMetrics("")
    val result = evaluator.evaluate(listOf(sessionTop1, sessionTop3, sessionTop3, sessionNone))
    Assertions.assertTrue(result.isNotEmpty())
  }

  @Test
  fun `test found@1 metric`() {
    val metric = RecallAtMetric(false, 1)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(0.25, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop3, sessionTop5, sessionNone)))
  }

  @Test
  fun `test found@5 metric`() {
    val metric = RecallAtMetric(false, 5)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(0.75, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNone)))
  }

  @Test
  fun `test Recall@1 metric`() {
    val metric = RecallAtMetric(showByDefault = false, n = 1)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1Custom)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1Custom, sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNoMatchCustom)))
  }

  @Test
  fun `test Cancelled metric`() {
    val metric = CancelledMetric(showByDefault = false)
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1Custom, sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1Custom, sessionNoSuggestionsCustom)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1Custom, sessionNoMatchCustom)))
    Assertions.assertEquals(0.25, metric.evaluate(listOf(
      sessionTop1Custom,
      sessionNoTop1Custom,
      sessionNoMatchCustom,
      sessionNoSuggestionsCustom
    )))
  }

  @Test
  fun `test Cancelled@1 metric`() {
    val metric = CancelledAtMetric(showByDefault = false, n = 1)
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1Custom)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1Custom, sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1Custom, sessionNoSuggestionsCustom)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(
      sessionTop1Custom,
      sessionNoTop1Custom,
      sessionNoMatchCustom,
      sessionNoSuggestionsCustom
    )))
  }

  @Test
  fun `test MatchedRatio@1 metric`() {
    val metric = MatchedRatioAt(false, 1)
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1Custom)))
    Assertions.assertEquals(0.25, metric.evaluate(listOf(sessionTop1Custom, sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNoMatchCustom)))
  }

  @Test
  fun `test MatchedRatio@3 metric`() {
    val metric = MatchedRatioAt(showByDefault = false, n = 3)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1Custom)))
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1Custom, sessionNoTop1Custom)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNoMatchCustom)))
  }

  @Test
  fun `test recall metric`() {
    val metric = RecallMetric(showByDefault = false)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5)))
    Assertions.assertEquals(0.75, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNone)))
  }

  @Test
  fun `test mean rank metric`() {
    val metric = MeanRankMetric()
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(2.0, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5)))
    Assertions.assertEquals(2.0, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(Double.NaN, metric.evaluate(listOf(sessionNone)))
  }

  @Test
  fun `test sessions count metric`() {
    val metric = SessionsCountMetric()
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(2.0, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(4.0, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
  }

  private fun mockSession(session: Session, expected: Int?, total: Int) {
    Mockito.`when`(session.lookups).thenReturn(listOf(createLookup(expected, total)))
    Mockito.`when`(session.expectedText).thenReturn(EXPECTED)
  }

  private fun mockSessionCustom(session: Session, expected: String, suggestions: List<String>) {
    Mockito.`when`(session.lookups)
      .thenReturn(
        listOf(
          Lookup.fromExpectedText(
            expected,
            "",
            suggestions.map { Suggestion(it, "", SuggestionSource.STANDARD)},
            0,
            comparator = { generated, expected_ -> !(generated.isEmpty() || !expected_.startsWith(generated))}
          )
        )
      )
    Mockito.`when`(session.expectedText).thenReturn(expected)
  }

  private fun createLookup(expected: Int?, total: Int): Lookup =
    Lookup.fromExpectedText(EXPECTED, "", createSuggestions(total, expected), 0,
                            comparator = { generated, expected_ -> !(generated.isEmpty() || !expected_.startsWith(generated))})

  private fun createSuggestions(total: Int, expected: Int?): List<Suggestion> =
    (0 until total).map {
      Suggestion(
        if (it == expected) EXPECTED else UNEXPECTED,
        presentationText = "",
        SuggestionSource.STANDARD)
    }
}
