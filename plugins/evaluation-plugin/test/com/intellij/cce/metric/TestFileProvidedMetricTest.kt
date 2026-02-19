package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestFileProvidedMetricTest {

  @Test
  fun `test evaluate with empty sessions`() {
    val metric = TestFileProvidedMetric()

    val sessions = emptyList<Session>()

    val result = metric.evaluate(sessions)

    assertEquals(Double.NaN, result, "Metric should return Double.NaN for empty sessions.")
  }

  @Test
  fun `test evaluate with all sessions providing test file`() {
    val metric = TestFileProvidedMetric()

    val sessions = listOf(
      createSessionWithTestFileProvided(true),
      createSessionWithTestFileProvided(true)
    )

    // Evaluate and assert that the metric returns 1.0, as all sessions provide the test file
    val result = metric.evaluate(sessions)
    assertEquals(1.0, result, "Metric should return 1.0 when all sessions provide test file.")
  }

  @Test
  fun `test evaluate with no sessions providing test file`() {
    val metric = TestFileProvidedMetric()

    val sessions = listOf(
      createSessionWithTestFileProvided(false),
      createSessionWithTestFileProvided(false)
    )

    // Evaluate and assert that the metric returns 0.0, as no sessions provide the test file
    val result = metric.evaluate(sessions)
    assertEquals(0.0, result, "Metric should return 0.0 when no sessions provide test file.")
  }

  @Test
  fun `test evaluate with mixed sessions`() {
    val metric = TestFileProvidedMetric()

    val sessions = listOf(
      createSessionWithTestFileProvided(true),
      createSessionWithTestFileProvided(false)
    )

    // Evaluate and assert that the metric returns the proportion of sessions providing the test file
    val result = metric.evaluate(sessions)
    assertEquals(0.5, result, "Metric should return the proportion of sessions providing the test file.")
  }

  @Test
  fun `test evaluate with invalid test file value`() {
    val metric = TestFileProvidedMetric()

    val sessions = listOf(createSessionWithInvalidTestFileValue())

    // Evaluate and assert that the result is Double.NaN for sessions with invalid values
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Metric should return Double.NaN for sessions with invalid test file value.")
  }

  @Test
  fun `test sample persistence across evaluations`() {
    val metric = TestFileProvidedMetric()

    val sessions1 = listOf(createSessionWithTestFileProvided(true))
    val sessions2 = listOf(createSessionWithTestFileProvided(false))

    metric.evaluate(sessions1)
    metric.evaluate(sessions2)

    // Assert that the mean persists across evaluations
    assertEquals(0.5, metric.value, "Metric sample mean should persist across evaluations.")
  }

  // Helper function to create a session indicating whether the test file was provided
  private fun createSessionWithTestFileProvided(provided: Boolean): Session {
    val additionalInfo = mapOf(AIA_TEST_FILE_PROVIDED to provided)
    val lookup = getDefaultLookup(additionalInfo)

    return getDefaultSession().apply {
      addLookup(lookup)
    }
  }

  // Helper function to create a session with an invalid test file value
  private fun createSessionWithInvalidTestFileValue(): Session {
    val additionalInfo = mapOf(AIA_TEST_FILE_PROVIDED to "invalid")
    val lookup = getDefaultLookup(additionalInfo)

    return getDefaultSession().apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a default Lookup object with additional info
  private fun getDefaultLookup(additionalInfo: Map<String, Any>) = Lookup(
    prefix = "test",
    offset = 0,
    suggestions = emptyList(), // No suggestions
    latency = 0L,
    features = null,
    selectedPosition = -1,
    isNew = true,
    additionalInfo = additionalInfo
  )

  // Helper function for creating a default Session object
  private fun getDefaultSession() = Session(
    offset = 0,
    expectedText = "test", // Default expected text
    completableLength = 0,
    _properties = SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {} // Empty properties
  )
}