package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecutionSuccessRatioTest {

  @Test
  fun `test evaluate with empty sessions`() {
    // Test the behavior when there are no sessions provided
    val metric = ExecutionSuccessRatio()
    val sessions = emptyList<Session>()

    // Expect the result to be 0.0 since there are no sessions to evaluate
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Execution success ratio for empty sessions should be Double.NaN")
  }

  @Test
  fun `test evaluate with valid success ratios`() {
    // Test when valid success ratios are provided across multiple sessions
    val metric = ExecutionSuccessRatio()
    val values = listOf(0.8, 1.0, 0.9) // Example success ratios
    val sessions = listOf(
      createSessionWithSuccessRatio(values[0]),
      createSessionWithSuccessRatio(values[1]),
      createSessionWithSuccessRatio(values[2])
    )

    // The expected ratio should be the average of the provided success ratios
    val expectedRatio = values.sum() / values.size
    val result = metric.evaluate(sessions)

    // Assert that the metric calculates the correct mean success ratio
    assertEquals(expectedRatio, result, "Execution success ratio should be the mean of valid success ratios.")
  }

  @Test
  fun `test sample persistence across evaluations`() {
    // Test that the metric maintains the correct state across multiple evaluations
    val metric = ExecutionSuccessRatio()
    val values = listOf(0.5, 0.7) // Success ratios for two separate evaluations
    val sessions1 = listOf(createSessionWithSuccessRatio(0.5))
    val sessions2 = listOf(createSessionWithSuccessRatio(0.7))

    // First evaluation
    metric.evaluate(sessions1)

    // Second evaluation
    metric.evaluate(sessions2)

    // Calculate the expected global mean (average of all success ratios across both evaluations)
    val expectedGlobalMean = values.sum() / values.size

    // Assert that the global mean is correctly maintained
    assertEquals(expectedGlobalMean, metric.value, "Global sample mean should persist across evaluations.")
  }

  @Test
  fun `test evaluate with missing success ratio`() {
    // Test when a session lacks a success ratio (missing value)
    val metric = ExecutionSuccessRatio()
    val sessions = listOf(createSessionWithMissingSuccessRatio())

    // If the success ratio is missing, it should be treated as 0.0 in the evaluation
    val result = metric.evaluate(sessions)
    assertEquals(0.0, result, "Execution success ratio for sessions with missing success ratio should be 0.0")
  }

  @Test
  fun `test evaluate with invalid success ratio value`() {
    // Test when a session contains an invalid success ratio value (not a valid number)
    val metric = ExecutionSuccessRatio()
    val sessions = listOf(createSessionWithInvalidSuccessRatio())

    // If the success ratio is invalid (e.g., "invalid"), it should be treated as 0.0
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Execution success ratio for sessions with invalid success ratio should be Double.NaN")
  }

  @Test
  fun `test evaluate with only one session`() {
    // Test with a single session containing a valid success ratio
    val metric = ExecutionSuccessRatio()
    val sessions = listOf(createSessionWithSuccessRatio(0.75))

    // The result should be the success ratio of the only session provided
    val result = metric.evaluate(sessions)
    assertEquals(0.75, result, "Execution success ratio for a single session should be the success ratio of that session.")
  }

  @Test
  fun `test evaluate with identical success ratios across sessions`() {
    // Test when all sessions have the same success ratio
    val metric = ExecutionSuccessRatio()
    val successRatio = 1.0 // All sessions will have this success ratio
    val sessions = listOf(
      createSessionWithSuccessRatio(successRatio),
      createSessionWithSuccessRatio(successRatio),
      createSessionWithSuccessRatio(successRatio)
    )

    // If all sessions have identical success ratios, the result should be that success ratio
    val result = metric.evaluate(sessions)
    assertEquals(successRatio, result, "Execution success ratio for sessions with identical success ratios should be that ratio.")
  }

  @Test
  fun `test evaluate with zero success ratio`() {
    // Test when the sessions have a success ratio value of 0.0
    val metric = ExecutionSuccessRatio()
    val sessions = listOf(
      createSessionWithSuccessRatio(0.0),
      createSessionWithSuccessRatio(0.0)
    )

    // If all sessions have zero success ratio, the result should be 0.0
    val result = metric.evaluate(sessions)
    assertEquals(0.0, result, "Execution success ratio for sessions with zero success ratio should be 0.0.")
  }

  @Test
  fun `test evaluate with negative success ratio value`() {
    // Test when a session has a negative success ratio value
    val metric = ExecutionSuccessRatio()
    val sessions = listOf(createSessionWithSuccessRatio(-0.5))

    // Negative success ratios are invalid and should be treated as 0.0
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Execution success ratio for sessions with negative success ratio should be Double.NaN.")
  }

  // Helper function for creating a session with a valid success ratio value
  private fun createSessionWithSuccessRatio(successRatio: Double): Session {
    val additionalInfo = mapOf(AIA_EXECUTION_SUCCESS_RATIO to successRatio)
    val lookup = getDefaultLookup(additionalInfo)

    return getDefaultSession().apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a session with missing success ratio
  private fun createSessionWithMissingSuccessRatio(): Session {
    val additionalInfo = emptyMap<String, Any>() // No success ratio data
    val lookup = getDefaultLookup(additionalInfo)

    return getDefaultSession().apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a session with invalid success ratio (non-numeric value)
  private fun createSessionWithInvalidSuccessRatio(): Session {
    val additionalInfo = mapOf(AIA_EXECUTION_SUCCESS_RATIO to "invalid") // Invalid success ratio value
    val lookup = getDefaultLookup(additionalInfo)

    return getDefaultSession().apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a default Lookup object
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
    _properties = SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {}
  )
}
