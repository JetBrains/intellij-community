package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.execution.coverage.PythonTestCoverageProcessor
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.execution.output.PythonErrorLogProcessor
import com.intellij.cce.execution.output.PythonProcessExecutionLog
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

class PythonCodeExecutionManager() : CodeExecutionManager() {
  override val language = Language.PYTHON

  private val defaultTestFilePath = "/tests/eval-plugin-test.py"


  override fun getGeneratedCodeFile(code: String): File {
    // ToDo check file path is provided in generated code
    return File(project.basePath + defaultTestFilePath)
  }

  override fun saveGeneratedCodeFile(code: String) {
    if (!codeFilePath.exists()) {
      codeFilePath.createNewFile()
    }
    codeFilePath.writeText(code)
  }

  override fun compileGeneratedCode(): ProcessExecutionLog {
    // NA
    return PythonProcessExecutionLog("", "", 0)
  }

  override fun executeGeneratedCode(target: String): ProcessExecutionLog {
    val bashScriptFile = File("${project.basePath}/bash_script_setup_tests.sh")

    if (!bashScriptFile.exists()) return PythonProcessExecutionLog("", "Bash script file not found", -1)
    if (!codeFilePath.exists()) return PythonProcessExecutionLog("", "The Python test file does not exist", -1)

    val testName = codeFilePath.path.removePrefix(project.basePath.toString()).removePrefix("/").removeSuffix(".py").replace("/", ".")
    val processBuilder = ProcessBuilder("/bin/bash", bashScriptFile.path.toString(), testName, target)

    processBuilder.environment()["PYTHON"] = ProjectRootManager.getInstance(project).projectSdk!!.homePath


    processBuilder.directory(File(project.basePath!!))

    try {
      // Start the process
      val process = processBuilder.start()
      // Capture and print the output
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()
      val coverageFilePath = "${project.basePath}/$testName-coverage"
      // Wait for the process to finish and get the exit code
      val exitCode = process.waitFor()

      // Collect execution information
      // Collect Test Failure
      val successRatio = PythonErrorLogProcessor(error).getTestExecutionSuccessRate()
      collectedInfo.put(AIA_EXECUTION_SUCCESS_RATIO, successRatio)
      // Collect Coverage
      val coverageProcessor = PythonTestCoverageProcessor(coverageFilePath)
      val lineCoverage = coverageProcessor.getLineCoverage()
      collectedInfo.put(AIA_TEST_LINE_COVERAGE, lineCoverage)
      val branchCoverage = coverageProcessor.getBranchCoverage()
      collectedInfo.put(AIA_TEST_BRANCH_COVERAGE, branchCoverage)

      return PythonProcessExecutionLog(output, error, exitCode, collectedInfo)
    }
    catch (e: Exception) {
      e.printStackTrace()
      return PythonProcessExecutionLog("", "", -1, collectedInfo)
    }
  }
}