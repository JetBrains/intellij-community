package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

class PytestPythonErrorLogProcessor : PythonErrorLogProcessor {
  private val testsRunRegex = Regex("""collected (\d+) items""")
  private val testErrorOrFailureRegex = Regex("""(\d+) (failed|error)""")

  override fun getTestExecutionSuccessRate(executionLog: ProcessExecutionLog): Double {

    // For pytest we are parsing stdout stream
    val logOutputString = executionLog.out

    // Extract the total number of tests run
    val testsRun = testsRunRegex.find(logOutputString)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    //  Extract the total number of tests with errors
    val failedTestCases = testErrorOrFailureRegex.find(logOutputString)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // Calculate the success ratio
    return if (testsRun > 0) {
      (testsRun - failedTestCases).toDouble() / testsRun
    }
    else {
      0.0 // Default to 0.0 if no tests were run
    }
  }
}
