package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.execution.output.PythonProcessExecutionLog

class PythonCodeExecutionManager() : CodeExecutionManager {
  override val language = Language.PYTHON
  override var isTest: Boolean = false

  override fun saveFile(): String {
    TODO("Not yet implemented: we should save the test code into a proper file in the projecy under test")
  }

  override fun compile(): ProcessExecutionLog {
    // NA
    return PythonProcessExecutionLog("", "")
  }

  override fun execute(): ProcessExecutionLog {
    println("This is Python test generation manager running ...")
    TODO("Not yet implemented, we should initiate a process")
    return PythonProcessExecutionLog("", "")
  }
}