package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BranchCoverageMetricTest {

  @Test
  fun `test evaluate with empty sessions`() {
    // Test the behavior when there are no sessions provided
    val metric = BranchCoverageMetric()
    val sessions = emptyList<Session>()

    // Expect the result to be 0.0 since there are no sessions to evaluate
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Branch coverage for empty sessions should be Double.NaN")
  }

  @Test
  fun `test evaluate with valid branch coverage`() {
    // Test when valid coverage values are provided across multiple sessions
    val metric = BranchCoverageMetric()
    val values = listOf(0.8, 0.6, 1.0) // Example coverage values
    val sessions = listOf(
      createSessionWithCoverage(values[0]),
      createSessionWithCoverage(values[1]),
      createSessionWithCoverage(values[2])
    )

    // The expected coverage should be the average of the provided values
    val expectedCoverage = values.sum() / values.size
    val result = metric.evaluate(sessions)

    // Assert that the metric calculates the correct mean coverage
    assertEquals(expectedCoverage, result, "Branch coverage should be the mean of valid coverage values.")
  }

  @Test
  fun `test sample persistence across evaluations`() {
    // Test that the metric maintains the correct state across multiple evaluations
    val metric = BranchCoverageMetric()
    val values = listOf(0.5, 0.8) // Coverage values for two separate evaluations
    val sessions1 = listOf(createSessionWithCoverage(0.5))
    val sessions2 = listOf(createSessionWithCoverage(0.8))

    // First evaluation
    metric.evaluate(sessions1)

    // Second evaluation
    metric.evaluate(sessions2)

    // Calculate the expected global mean (average of all coverage values across both evaluations)
    val expectedGlobalMean = values.sum() / values.size

    // Assert that the global mean is correctly maintained
    assertEquals(expectedGlobalMean, metric.value, "Global sample mean should persist across evaluations.")
  }

  @Test
  fun `test evaluate with missing branch coverage value`() {
    // Test when a session lacks a coverage value (coverage missing)
    val metric = BranchCoverageMetric()
    val sessions = listOf(createSessionWithMissingCoverage())

    // If the coverage is missing, it should be treated as 0.0 in the evaluation
    val result = metric.evaluate(sessions)
    assertEquals(0.0, result, "Branch coverage for sessions with missing coverage should be 0.0")
  }

  @Test
  fun `test evaluate with invalid branch coverage value`() {
    // Test when a session contains an invalid coverage value (not a valid number)
    val metric = BranchCoverageMetric()
    val sessions = listOf(createSessionWithInvalidCoverage())

    // If the coverage is invalid (e.g., "invalid"), it should be treated as 0.0
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Branch coverage for sessions with invalid coverage should be Double.NaN")
  }

  @Test
  fun `test evaluate with only one session`() {
    // Test with a single session containing a valid coverage value
    val metric = BranchCoverageMetric()
    val sessions = listOf(createSessionWithCoverage(0.75))

    // The result should be the coverage of the only session provided
    val result = metric.evaluate(sessions)
    assertEquals(0.75, result, "Branch coverage for a single session should be the coverage of that session.")
  }

  @Test
  fun `test evaluate with identical coverage values across sessions`() {
    // Test when all sessions have the same coverage value
    val metric = BranchCoverageMetric()
    val coverage = 0.9 // All sessions will have this coverage value
    val sessions = listOf(
      createSessionWithCoverage(coverage),
      createSessionWithCoverage(coverage),
      createSessionWithCoverage(coverage)
    )

    // If all sessions have identical coverage, the result should be that coverage value
    val result = metric.evaluate(sessions)
    assertEquals(coverage, result, "Branch coverage for sessions with identical coverage should be that coverage value.")
  }

  @Test
  fun `test evaluate with zero branch coverage`() {
    // Test when the sessions have a branch coverage value of 0.0
    val metric = BranchCoverageMetric()
    val sessions = listOf(
      createSessionWithCoverage(0.0),
      createSessionWithCoverage(0.0)
    )

    // If all sessions have zero coverage, the result should be 0.0
    val result = metric.evaluate(sessions)
    assertEquals(0.0, result, "Branch coverage for sessions with zero coverage should be 0.0.")
  }

  @Test
  fun `test evaluate with negative branch coverage value`() {
    // Test when a session has a negative coverage value
    val metric = BranchCoverageMetric()
    val sessions = listOf(createSessionWithCoverage(-0.5))

    // Negative coverage values are invalid and should be treated as 0.0
    val result = metric.evaluate(sessions)
    assertEquals(Double.NaN, result, "Branch coverage for sessions with negative coverage should be Double.NaN.")
  }

  // Helper function for creating a session with a valid coverage value
  private fun createSessionWithCoverage(coverage: Double): Session {
    val additionalInfo = mapOf(AIA_TEST_BRANCH_COVERAGE to coverage)
    val lookup = getDefaultLookup(additionalInfo)

    val tokenProperties = SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {
      put("coverage", coverage.toString()) // Add coverage as a property
    }

    // Create a session with the given properties and lookup
    return getDefaultSession(tokenProperties).apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a session with missing coverage
  private fun createSessionWithMissingCoverage(): Session {
    val additionalInfo = emptyMap<String, Any>() // No coverage data
    val lookup = getDefaultLookup(additionalInfo)

    val tokenProperties = SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {}

    return getDefaultSession(tokenProperties).apply {
      addLookup(lookup)
    }
  }

  // Helper function for creating a session with invalid coverage (non-numeric value)
  private fun createSessionWithInvalidCoverage(): Session {
    val additionalInfo = mapOf(AIA_TEST_BRANCH_COVERAGE to "invalid") // Invalid coverage value
    val lookup = getDefaultLookup(additionalInfo)

    val tokenProperties = SimpleTokenProperties.create(TypeProperty.TOKEN, SymbolLocation.UNKNOWN) {}

    return getDefaultSession(tokenProperties).apply {
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
  private fun getDefaultSession(properties: TokenProperties) = Session(
    offset = 0,
    expectedText = "test", // Default expected text
    completableLength = 0,
    _properties = properties // Token properties passed as argument
  )
}
