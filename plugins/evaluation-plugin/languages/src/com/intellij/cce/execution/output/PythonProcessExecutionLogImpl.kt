package com.intellij.cce.execution.output

class ProcessExecutionLogImpl(
  override val out: String,
  override val error: String,
  override val exitCode: Int,
) : ProcessExecutionLog