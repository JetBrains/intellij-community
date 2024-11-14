package com.intellij.cce.execution.output

class PythonProcessExecutionLog(
  override val out: String,
  override val error: String,
  override val exitCode: Int,
) : ProcessExecutionLog