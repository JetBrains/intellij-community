package com.intellij.cce.execution.output

data class ProcessExecutionLog(
  val out: String,
  val error: String,
  val exitCode: Int,
)