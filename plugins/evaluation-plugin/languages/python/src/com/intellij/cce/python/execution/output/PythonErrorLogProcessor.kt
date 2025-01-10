package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

class PythonErrorLogProcessor(val executionLog: ProcessExecutionLog) {

  fun getTestExecutionSuccessRate(): Double {
    val testsRunRegex = Regex("""Ran (\d+) tests?""")
    val testErrorOrFailureRegex = Regex("""(ERROR|FAIL): (.+?)\n""")

    val errorLog = executionLog.out + executionLog.error

    // Extract the total number of tests run
    val testsRun = testsRunRegex.find(errorLog)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // Count unique tests with errors
    val failedTestCases = testErrorOrFailureRegex.findAll(errorLog).map { it.groupValues[2] }.distinct().count()

    // Calculate the success ratio
    return if (testsRun > 0) {
      (testsRun - failedTestCases).toDouble() / testsRun
    }
    else {
      0.0 // Default to 0.0 if no tests were run
    }
  }
}