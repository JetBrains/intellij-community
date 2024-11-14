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
    return PythonProcessExecutionLog("", "")
  }

  override fun execute(): ProcessExecutionLog {
    println("This is Python test generation manager running ...")
    //TODO("Not yet implemented, we should initiate a process")
    //val bashScriptPath = File("${PathManager.getHomePath()}/community/plugins/evaluation-plugin/languages/python/src/com/intellij/cce/execution/manager/bash_script_setup_tests.sh").absolutePath
    //val processBuilder = ProcessBuilder("/bin/bash",  bashScriptPath, testPath)

    val processBuilder = ProcessBuilder("python3", testPath)

    //processBuilder.environment()["PYTHON"] = sdk.homePath

    //processBuilder.directory(File("~/ul"))


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
    }
    catch (e: Exception) {
      e.printStackTrace()
    }


    return PythonProcessExecutionLog("", "")
  }
}