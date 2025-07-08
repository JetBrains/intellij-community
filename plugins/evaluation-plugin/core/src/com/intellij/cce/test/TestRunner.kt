package com.intellij.cce.test

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

class TestRunnerParams(val language: Language)

class TestRunRequest(val tests: List<String>, val project: Project)
class TestRunResult(
  val exitCode: Int, val passed: List<String>,
  val failed: List<String>,
  val compilationSuccessful: Boolean,
  val projectIsResolvable: Boolean,
  val output: String,
)

interface TestRunner {
  companion object {
    private val EP_NAME: ExtensionPointName<TestRunner> =
      ExtensionPointName.Companion.create<TestRunner>("com.intellij.cce.testRunner")
    fun getTestRunner(params: TestRunnerParams): TestRunner {
      return EP_NAME.findFirstSafe { it.isApplicable(params) } ?: throw IllegalStateException("No test runner for $params")
    }
  }

  fun isApplicable(params: TestRunnerParams): Boolean

  suspend fun runTests(request: TestRunRequest): TestRunResult
}