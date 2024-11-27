package com.intellij.cce.python.execution.output

class PythonErrorLogProcessor(val errorLog: String) {

  fun getTestExecutionSuccessRate(): Double {
    val testsRunRegex = Regex("""Ran (\d+) tests?""")
    val testErrorOrFailureRegex = Regex("""(ERROR|FAIL): (.+?)\n""")

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