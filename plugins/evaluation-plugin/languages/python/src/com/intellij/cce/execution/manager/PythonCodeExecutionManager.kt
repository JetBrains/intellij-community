package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.execution.output.PythonProcessExecutionLog
import com.intellij.openapi.application.PathManager
import java.io.File

class PythonCodeExecutionManager() : CodeExecutionManager {
  override val language = Language.PYTHON
  override var isTest: Boolean = false
  val testPath: String = "${PathManager.getBinPath()}/tests/test.py"

  override fun saveFile(code: String) {
    File(testPath).writeText(code)
  }

  override fun compile(): ProcessExecutionLog {
    // NA
    return PythonProcessExecutionLog("", "", 0)
  }

  override fun execute(projectPath: String): ProcessExecutionLog {
    println("This is Python test generation manager running ...")
    val bashScriptFile = File("$projectPath/bash_script_setup_test.sh")
    if (!bashScriptFile.exists()) {
      // TODO provide the error
      return PythonProcessExecutionLog("", "Bash script file not found", -1)
    }

    val processBuilder = ProcessBuilder("/bin/bash", bashScriptFile.path.toString(), testPath)

    //processBuilder.environment()["PYTHON"] = TODO set correct python version

    processBuilder.directory(File(projectPath))

    try {
      // Start the process
      val process = processBuilder.start()
      // Capture and print the output
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()

      // Wait for the process to finish and get the exit code
      val exitCode = process.waitFor()
      // these should be saved in PythonProcessExecutionLog
      println("Output:\n$output")
      println("Error:\n$error")
      println("Exit code: $exitCode")
      return PythonProcessExecutionLog(output, error, exitCode)
    }
    catch (e: Exception) {
      e.printStackTrace()
      return PythonProcessExecutionLog("", "", 1)
    }
  }
}