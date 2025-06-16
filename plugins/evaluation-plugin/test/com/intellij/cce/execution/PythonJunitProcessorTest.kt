package com.intellij.cce.execution

import com.intellij.cce.python.execution.output.PythonJunitProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class PythonJunitProcessorTest {

  private val processor = PythonJunitProcessor()

  /**
   * Tests the success rate calculation when all tests pass with no failures, errors, or skipped tests.
   */
  @Test
  fun `test success rate with all tests passing`() {
    val xmlData = """
            <testsuite tests="10" failures="0" errors="0" skipped="0"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(1.0, result, 0.01)
  }

  /**
   * Tests the success rate calculation when some tests fail.
   */
  @Test
  fun `test success rate with some failures`() {
    val xmlData = """
            <testsuite tests="10" failures="2" errors="0" skipped="0"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.8, result, 0.01)
  }

  /**
   * Tests the success rate calculation when some tests have execution errors.
   */
  @Test
  fun `test success rate with some errors`() {
    val xmlData = """
            <testsuite tests="10" failures="0" errors="2" skipped="0"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.8, result, 0.01)
  }

  /**
   * Tests the success rate calculation when some tests are skipped.
   */
  @Test
  fun `test success rate with some skipped tests`() {
    val xmlData = """
            <testsuite tests="10" failures="0" errors="0" skipped="2"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.8, result, 0.01)
  }

  /**
   * Tests the success rate calculation with a mix of failures, errors, and skipped tests.
   */
  @Test
  fun `test success rate with a mix of failures, errors, and skipped tests`() {
    val xmlData = """
            <testsuite tests="10" failures="2" errors="1" skipped="1"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.6, result, 0.01)
  }

  /**
   * Tests the success rate calculation when there are zero tests executed.
   */
  @Test
  fun `test success rate with zero tests`() {
    val xmlData = """
            <testsuite tests="0" failures="0" errors="0" skipped="0"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.0, result, 0.01)
  }

  /**
   * Tests the success rate calculation when all tests fail.
   */
  @Test
  fun `test success rate with all tests failing`() {
    val xmlData = """
            <testsuite tests="10" failures="10" errors="0" skipped="0"/>
        """.trimIndent()

    val result = processor.getTestExecutionSuccessRate(xmlData)
    assertEquals(0.0, result, 0.01)
  }

  /**
   * Tests the behavior of the `getTestExecutionSuccessRate` method when the input XML is invalid.
   */
  @Test
  fun `test success rate with invalid xml`() {
    val xmlData = """
            <testsuite tests="10" failures="two" errors="0" skipped="0"/>
        """.trimIndent()

    try {
      processor.getTestExecutionSuccessRate(xmlData)
    } catch (e: Exception) {
      assertEquals(NumberFormatException::class.java, e::class.java)
    }
  }
}
