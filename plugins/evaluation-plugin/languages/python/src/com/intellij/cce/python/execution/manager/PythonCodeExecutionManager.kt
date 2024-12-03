package com.intellij.cce.python.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.execution.manager.CodeExecutionManager
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.python.execution.coverage.PythonTestCoverageProcessor
import com.intellij.cce.python.execution.output.PythonErrorLogProcessor
import com.intellij.cce.python.execution.output.PythonProcessExecutionLog
import com.jetbrains.python.sdk.pythonSdk
import java.io.File

class PythonCodeExecutionManager() : CodeExecutionManager() {
  override val language = Language.PYTHON

  private val defaultTestFilePath = "/tests/eval-plugin-test.py"

  override fun getGeneratedCodeFile(code: String): File {
    extractCodeDirectory(code)?.let {
      val detectedPath = if (!it.startsWith("/")) "/$it" else it
      collectedInfo.put(AIA_TEST_FILE_PROVIDED, true)
      return File(project.basePath + detectedPath)
    }
    collectedInfo.put(AIA_TEST_FILE_PROVIDED, false)
    return File(project.basePath + defaultTestFilePath)
  }

  override fun setupEnvironment(): PythonProcessExecutionLog {
    val setupFile = File("${project.basePath}/setup_tests.sh")
    if (!setupFile.exists()) return PythonProcessExecutionLog("", "Bash script file not found", -1)
    val executionLog =
      runPythonProcess(
        ProcessBuilder("/bin/bash", setupFile.path.toString())
      )

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

  override fun saveGeneratedCodeFile(code: String, codeFilePath: File) {
    if (!codeFilePath.exists()) {
      codeFilePath.createNewFile()
    }
    codeFilePath.writeText(code)
  }

  override fun compileGeneratedCode(): ProcessExecutionLog {
    // NA
    return PythonProcessExecutionLog("", "", 0)
  }

  override fun executeGeneratedCode(target: String, codeFilePath: File): ProcessExecutionLog {
    val runFile = File("${project.basePath}/run_tests.sh")

    if (!runFile.exists()) return PythonProcessExecutionLog("", "Bash script file not found", -1)
    if (!codeFilePath.exists()) return PythonProcessExecutionLog("", "The Python test file does not exist", -1)

    val testName = codeFilePath.path
      .removePrefix(project.basePath.toString())
      .removePrefix("/")
      .removeSuffix(".py")
      .replace("/", ".")

    val coverageFilePath = "${project.basePath}/$testName-coverage"

    project.pythonSdk ?: return PythonProcessExecutionLog("", "Python SDK not found", -1)

    try {
      val executionLog = runPythonProcess(
        ProcessBuilder("/bin/bash", runFile.path.toString(), testName, target)
      )
      // Collect Test Success Ratio
      val successRatio =
        PythonErrorLogProcessor(executionLog.error).getTestExecutionSuccessRate()

      collectedInfo.put(AIA_EXECUTION_SUCCESS_RATIO, successRatio)
      // Collect Coverage
      val coverageProcessor = PythonTestCoverageProcessor(coverageFilePath)
      val lineCoverage = coverageProcessor.getLineCoverage()
      collectedInfo.put(AIA_TEST_LINE_COVERAGE, lineCoverage)
      val branchCoverage = coverageProcessor.getBranchCoverage()
      collectedInfo.put(AIA_TEST_BRANCH_COVERAGE, branchCoverage)

      File(coverageFilePath).delete()
      File(project.basePath + "/.coverage").delete() // Remove cumulative coverage data for all the tests

      return executionLog
    }
    catch (e: Exception) {
      e.printStackTrace()
      return PythonProcessExecutionLog("", "", -1, collectedInfo)
    }
  }

  private fun runPythonProcess(processBuilder: ProcessBuilder):
    PythonProcessExecutionLog {
    // Set the correct Python interpreter
    processBuilder.environment()["PYTHON"] =  project.pythonSdk!!.homePath
    // Move to project's root
    processBuilder.directory(File(project.basePath!!))
    // Start the process
    val process = processBuilder.start()
    // Capture and print the output
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    // Wait for the process to finish and get the exit code
    val exitCode = process.waitFor()

    return PythonProcessExecutionLog(output, error, exitCode)
  }
}
