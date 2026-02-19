package com.intellij.cce.python.test

import com.intellij.cce.core.Language
import com.intellij.cce.test.TestRunRequest
import com.intellij.cce.test.TestRunResult
import com.intellij.cce.test.TestRunner
import com.intellij.cce.test.TestRunnerParams
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.guessProjectDir

class PythonTestRunner : TestRunner {

  override fun isApplicable(params: TestRunnerParams): Boolean =
    params.language == Language.PYTHON

  override suspend fun runTests(request: TestRunRequest): TestRunResult {
    // TODO unify with JavaTestRunner
    val testCommand = request.testCommand
    requireNotNull(testCommand) { "Only test running via command is supported" }

    val testCommandParts = testCommand.split(" ")

    val commandLine = GeneralCommandLine(testCommandParts[0])
      .withWorkingDirectory(request.project.guessProjectDir()!!.toNioPath())
      .also {
        testCommandParts.drop(1).forEach { param -> it.addParameter(param) }
      }

    val process = CapturingProcessHandler(commandLine).runProcess()

    val testResult =
      if (testCommand.contains("pytest")) pytest(process.stdoutLines, request.tests)
      else if (testCommand.contains("unittest")) unittest(process.stderrLines, request.tests)
      else error("Unsupported test command: $testCommand")

    return TestRunResult(
      exitCode = process.exitCode,
      passed = testResult.passedTests,
      failed = testResult.failedTests,
      expected = testResult.expectedTests,
      compilationSuccessful = true,
      projectIsResolvable = true,
      output = process.stdout + "\n\nSTDERR\n\n" + process.stderr,
    )
  }

  private fun pytest(output: List<String>, expectedTests: List<String>): TestResult {
    val passedTests = output
      .filter { it.contains("PASSED") }
      .map { it.substringBefore("PASSED").trim() }
      .filter { it.isNotEmpty() }
      .map(::normalizePytestName)

    val failedTests = output
      .filter { it.contains("FAILED") }
      .map { it.substringBefore("FAILED").trim() }
      .filter { it.isNotEmpty() }
      .map(::normalizePytestName)

    return TestResult(expectedTests, passedTests, failedTests)
  }

  private fun unittest(output: List<String>, expectedTests: List<String>): TestResult {
    val passedTests = output
      .filter { it.endsWith("... ok") }
      .map { it.substringBefore("... ok").trim() }
      .filter { it.isNotEmpty() }
      .map(::normalizeUnittestName)

    val failedTests = output
      .filter { it.endsWith("... ERROR") }
      .map { it.substringBefore("... ERROR").trim() }
      .map(::normalizeUnittestName)

    return TestResult(expectedTests.map { it.trim(':') }, passedTests, failedTests)
  }

  private fun normalizePytestName(testName: String): String =
    if (testName.indexOf("::") == testName.lastIndexOf("::")) testName.replace("::", ":None:")
    else testName.replace("::", ":")

  private fun normalizeUnittestName(testName: String): String =
    if (!testName.contains(" ")) testName.takeLastWhile { it != '.' } else testName

  private data class TestResult(val expectedTests: List<String>, val passedTests: List<String>, val failedTests: List<String>)
}