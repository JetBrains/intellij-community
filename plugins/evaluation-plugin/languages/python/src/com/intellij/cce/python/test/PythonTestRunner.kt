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
    val testCommand = System.getenv("EVALUATION_TEST_COMMAND")
    requireNotNull(testCommand) { "Only test running via command is supported" }

    val testCommandParts = testCommand.split(" ")

    val commandLine = GeneralCommandLine(testCommandParts[0])
      .withWorkingDirectory(request.project.guessProjectDir()!!.toNioPath())
      .also {
        testCommandParts.drop(1).forEach { param -> it.addParameter(param) }
      }

    val process = CapturingProcessHandler(commandLine).runProcess()

    val passedTests = process.stdoutLines
      .filter { it.contains("PASSED") }
      .map { it.substringBefore("PASSED").trim() }
      .filter { it.isNotEmpty() }
      .map(::normalizeTestName)

    val failedTests = process.stdoutLines
      .filter { it.contains("FAILED") }
      .map { it.substringBefore("FAILED").trim() }
      .filter { it.isNotEmpty() }
      .map(::normalizeTestName)

    return TestRunResult(
      exitCode = process.exitCode,
      passed = passedTests,
      failed = failedTests,
      expected = request.tests,
      compilationSuccessful = true,
      projectIsResolvable = true,
      output = process.stdout + "\n\nSTDERR\n\n" + process.stderr,
    )
  }

  private fun normalizeTestName(testName: String): String =
    testName.replace("::", ":None:")
}