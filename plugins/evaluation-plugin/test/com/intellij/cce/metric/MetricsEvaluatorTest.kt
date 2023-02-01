package com.intellij.cce.metric

import com.intellij.cce.actions.CompletionContext
import com.intellij.cce.actions.CompletionPrefix
import com.intellij.cce.actions.CompletionStrategy
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
    private val defaultStrategy = CompletionStrategy(CompletionPrefix.NoPrefix, CompletionContext.ALL,
                                                     emulateUser = false, completionGolf = null, filters = emptyMap()
    )

    private const val EXPECTED = "expected"
    private const val UNEXPECTED = "unexpected"
  }

  init {
    mockSession(sessionTop1, expected = 0, total = 5)
    mockSession(sessionTop3, expected = 2, total = 5)
    mockSession(sessionTop5, expected = 4, total = 5)
    mockSession(sessionNone, expected = null, total = 10)
  }

  @Test
  fun `test metrics evaluator`() {
    val evaluator = MetricsEvaluator.withDefaultMetrics("", defaultStrategy)
    val result = evaluator.evaluate(listOf(sessionTop1, sessionTop3, sessionTop3, sessionNone))
    Assertions.assertTrue(result.isNotEmpty())
  }

  @Test
  fun `test found@1 metric`() {
    val metric = RecallAtMetric(1)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(0.5, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(0.25, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionTop3, sessionTop5, sessionNone)))
  }

  @Test
  fun `test found@5 metric`() {
    val metric = RecallAtMetric(5)
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1)))
    Assertions.assertEquals(1.0, metric.evaluate(listOf(sessionTop1, sessionTop3)))
    Assertions.assertEquals(0.75, metric.evaluate(listOf(sessionTop1, sessionTop3, sessionTop5, sessionNone)))
    Assertions.assertEquals(0.0, metric.evaluate(listOf(sessionNone)))
  }

  @Test
  fun `test recall metric`() {
    val metric = RecallMetric()
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

  private fun createLookup(expected: Int?, total: Int): Lookup =
    Lookup.fromExpectedText(EXPECTED, "", createSuggestions(total, expected), 0)

  private fun createSuggestions(total: Int, expected: Int?): List<Suggestion> =
    (0 until total).map {
      Suggestion(
        if (it == expected) EXPECTED else UNEXPECTED,
        presentationText = "",
        SuggestionSource.STANDARD)
    }
}
