package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

class PythonErrorLogProcessor(val executionLog: ProcessExecutionLog) {

  fun getTestExecutionSuccessRate(): Double {
    val testsRunRegex = Regex("""Ran (\d+) tests?""")
    val testErrorOrFailureRegex = Regex("""(ERROR|FAIL): (.+?)\n""")

    // To extract correct information from different testing frameworks (e.g., pytest), we pare both log and err
    val logOutputString = executionLog.out + executionLog.error

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