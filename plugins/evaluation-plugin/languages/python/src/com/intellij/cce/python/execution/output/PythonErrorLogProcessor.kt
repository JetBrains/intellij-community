package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

interface PythonErrorLogProcessor {
  /**
   * Calculates the success rate of test execution based on the provided execution log.
   * The success rate is determined as the ratio of successfully executed tests
   * to the total number of tests executed.
   *
   * @param executionLog The execution log containing information about test execution including
   *                     stdout, stderr, and exit code.
   * @return The success rate as a Double value in the range [0.0, 1.0]. Returns 0.0 if no tests were executed.
   */
  fun getTestExecutionSuccessRate(executionLog: ProcessExecutionLog): Double
}
