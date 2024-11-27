package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

class PythonProcessExecutionLog(
  override val out: String,
  override val error: String,
  override val exitCode: Int,
  override val collectedInfo: MutableMap<String, Any> = mutableMapOf()
) : ProcessExecutionLog