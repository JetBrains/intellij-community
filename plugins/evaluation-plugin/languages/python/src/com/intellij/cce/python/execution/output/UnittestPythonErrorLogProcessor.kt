package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

class UnittestPythonErrorLogProcessor: PythonErrorLogProcessor {
  override fun getTestExecutionSuccessRate(executionLog: ProcessExecutionLog): Double {
    val testsRunRegex = Regex("""Ran (\d+) tests?""")
    val testErrorOrFailureRegex = Regex("""(ERROR|FAIL): (.+?)\n""")

    // For unittest we parse stderr stream
    val logOutputString = executionLog.error

    // Extract the total number of tests run
    val testsRun = testsRunRegex.find(logOutputString)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // Count unique tests with errors
    val failedTestCases = testErrorOrFailureRegex.findAll(logOutputString).map { it.groupValues[2] }.distinct().count()

    // Calculate the success ratio
    return if (testsRun > 0) {
      (testsRun - failedTestCases).toDouble() / testsRun
    }
    else {
      0.0 // Default to 0.0 if no tests were run
    }
  }
}
