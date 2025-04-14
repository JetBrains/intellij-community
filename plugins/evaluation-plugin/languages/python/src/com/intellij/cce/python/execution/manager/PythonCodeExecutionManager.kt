package com.intellij.cce.python.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.evaluation.data.ExecutionMode
import com.intellij.cce.execution.manager.CodeExecutionManager
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.python.execution.coverage.PythonTestCoverageProcessor
import com.intellij.cce.python.execution.output.PythonJunitProcessor

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiNamedElement
import com.intellij.util.io.delete
import com.jetbrains.python.sdk.PythonSdkType
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

open class PythonCodeExecutionManager() : CodeExecutionManager() {
  override val language = Language.PYTHON
  override val executionMode = ExecutionMode.LOCAL

  private val defaultTestFilePath = "/tests/eval-plugin-test.py"

  override fun getGeneratedCodeFile(basePath: String, code: String): Path {
    extractCodeDirectory(code)?.let {
      val detectedPath = if (!it.startsWith("/")) "/$it" else it
      collectedInfo.put(AIA_TEST_FILE_PROVIDED, true)
      return Path.of(basePath + detectedPath)
    }
    collectedInfo.put(AIA_TEST_FILE_PROVIDED, false)
    return Path.of(basePath + defaultTestFilePath)
  }

  override fun setupEnvironment(project: Project, sdk: Sdk?) {
    val basePath = project.basePath

    basePath ?: return

    if (sdk?.sdkType !is PythonSdkType) return

    val setupFile = Path.of("$basePath/setup_tests.sh")
    if (!setupFile.exists()) return
    val executionLog = runPythonProcess(basePath, ProcessBuilder("/bin/bash", setupFile.toString()), sdk)

    if (executionLog.exitCode != 0) throw IllegalStateException("Setup was not successful")
  }

  private fun extractCodeDirectory(code: String): String? {
    // Regular expression to match the comment line with the directory
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

  override fun executeGeneratedCode(target: String, basePath: String, codeFilePath: Path, sdk: Sdk?, unitUnderTest: PsiNamedElement?): ProcessExecutionLog {
    if (sdk?.sdkType !is PythonSdkType) return ProcessExecutionLog("", "Python SDK not found", -1)

    val runFile = Path.of("$basePath/run_tests.sh")

    if (!runFile.exists()) return ProcessExecutionLog("", "Bash script file not found", -1)
    if (!codeFilePath.exists()) return ProcessExecutionLog("", "The Python test file does not exist", -1)

    val testName = codeFilePath
      .toAbsolutePath()
      .normalize()
      .toString()
      .removePrefix(basePath)
      .removePrefix("/")
      .removeSuffix(".py")

    val coverageFilePath = "$basePath/$testName-coverage"
    val junitFilePath = "$basePath/$testName-junit"
    try {
      val executionLog = runPythonProcess(basePath, ProcessBuilder("/bin/bash", runFile.toString(), testName, target), sdk)

      // Collect success ratio
      val junitFile = Path.of(junitFilePath)
      val junitData = if (junitFile.exists()) junitFile.readText(Charsets.UTF_8) else ""
      val successRatio = PythonJunitProcessor().getTestExecutionSuccessRate(junitData)
      collectedInfo.put(AIA_EXECUTION_SUCCESS_RATIO, successRatio)

      // Collect Coverage
      val coverageFile = Path.of(coverageFilePath)
      val coverageData = if (coverageFile.exists()) coverageFile.readText(Charsets.UTF_8) else ""
      val coverageProcessor = PythonTestCoverageProcessor(coverageData, target)

      collectedInfo.put(AIA_TEST_LINE_COVERAGE, coverageProcessor.getLineCoverage(unitUnderTest))
      collectedInfo.put(AIA_TEST_BRANCH_COVERAGE, coverageProcessor.getBranchCoverage(unitUnderTest))

      // Remove cumulative coverage data for all the tests
      Path.of(coverageFilePath).delete()
      Path.of(junitFilePath).delete()
      Path.of("$basePath/.coverage").delete()
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
    processBuilder.directory(Path.of(basePath).toFile())
    // Start the process
    val process = processBuilder.start()
    // Capture and print the output
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    // Wait for the process to finish and get the exit code
    val exitCode = process.waitFor()

    return ProcessExecutionLog(output, error, exitCode)
  }

  override fun removeEnvironment() {}
}
