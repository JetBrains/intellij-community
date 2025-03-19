package com.intellij.cce.python.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.execution.manager.CodeExecutionManager
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.python.execution.coverage.PythonTestCoverageProcessor
import com.intellij.cce.python.execution.output.PythonErrorLogProcessorFactory
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkType
import java.io.File

class PythonCodeExecutionManager() : CodeExecutionManager() {
  override val language = Language.PYTHON

  private val defaultTestFilePath = "/tests/eval-plugin-test.py"

  override fun getGeneratedCodeFile(basePath: String, code: String): File {
    extractCodeDirectory(code)?.let {
      val detectedPath = if (!it.startsWith("/")) "/$it" else it
      collectedInfo.put(AIA_TEST_FILE_PROVIDED, true)
      return File(basePath + detectedPath)
    }
    collectedInfo.put(AIA_TEST_FILE_PROVIDED, false)
    return File(basePath + defaultTestFilePath)
  }

  override fun setupEnvironment(basePath: String, sdk: Sdk?): ProcessExecutionLog {
    if (sdk?.sdkType !is PythonSdkType) return ProcessExecutionLog("", "Python SDK not found", -1)

    val setupFile = File("$basePath/setup_tests.sh")
    if (!setupFile.exists()) return ProcessExecutionLog("", "Bash script file not found", -1)
    val executionLog = runPythonProcess(basePath, ProcessBuilder("/bin/bash", setupFile.path.toString()), sdk)

    if (executionLog.exitCode != 0) throw IllegalStateException("Setup was not successful")

    return executionLog
  }

  private fun extractCodeDirectory(code: String): String? {
    // Regular expression to match the comment line with directory
    val pattern = Regex("""^#\s*(?:[Ff]ile:\s*)?(.*)""") // Accepts both # file: <directory> and  # <directory>

    // Split the code into lines, take the first line, and trim it
    val firstLine = code.lines().firstOrNull()?.trim() ?: return null

    // Match the pattern in the first line
    val match = pattern.matchEntire(firstLine)
    return match?.groups?.get(1)?.value?.trim()
  }

  override fun compileGeneratedCode(): ProcessExecutionLog {
    // NA
    return ProcessExecutionLog("", "", 0)
  }

  override fun executeGeneratedCode(target: String, basePath: String, codeFilePath: File, sdk: Sdk?, testingFramework: String?): ProcessExecutionLog {
    if (sdk?.sdkType !is PythonSdkType) return ProcessExecutionLog("", "Python SDK not found", -1)

    val runFile = File("$basePath/run_tests.sh")

    if (!runFile.exists()) return ProcessExecutionLog("", "Bash script file not found", -1)
    if (!codeFilePath.exists()) return ProcessExecutionLog("", "The Python test file does not exist", -1)

    val testName = codeFilePath.path
      .removePrefix(basePath)
      .removePrefix("/")
      .removeSuffix(".py")
      .replace("/", ".")

    val coverageFilePath = "$basePath/$testName-coverage"
    try {
      val executionLog = runPythonProcess(basePath, ProcessBuilder("/bin/bash", runFile.path.toString(), testName, target), sdk)
      // Collect Test Success Ratio, different testing frameworks outputs information about tests into different streams
      val errorLogProcessor = PythonErrorLogProcessorFactory().createProcessor(testingFramework)
      val successRatio = errorLogProcessor.getTestExecutionSuccessRate(executionLog)
      collectedInfo.put(AIA_EXECUTION_SUCCESS_RATIO, successRatio)
      // Collect Coverage
      val coverageProcessor = PythonTestCoverageProcessor(coverageFilePath)
      val lineCoverage = coverageProcessor.getLineCoverage()
      collectedInfo.put(AIA_TEST_LINE_COVERAGE, lineCoverage)
      val branchCoverage = coverageProcessor.getBranchCoverage()
      collectedInfo.put(AIA_TEST_BRANCH_COVERAGE, branchCoverage)

      // Remove cumulative coverage data for all the tests
      File(coverageFilePath).delete()
      File("$basePath/.coverage").delete()
      return executionLog
    }
    catch (e: Exception) {
      e.printStackTrace()
      return ProcessExecutionLog("", "", -1)
    }
  }

  private fun runPythonProcess(basePath: String, processBuilder: ProcessBuilder, sdk: Sdk):
    ProcessExecutionLog {
    // Set the correct Python interpreter
    processBuilder.environment()["PYTHON"] = sdk.homePath
    // Move to project's root
    processBuilder.directory(File(basePath))
    // Start the process
    val process = processBuilder.start()
    // Capture and print the output
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    // Wait for the process to finish and get the exit code
    val exitCode = process.waitFor()

    return ProcessExecutionLog(output, error, exitCode)
  }
}
