package com.intellij.cce.python.execution.output

import com.intellij.cce.execution.output.ProcessExecutionLog

interface PythonErrorLogProcessor {
  fun getTestExecutionSuccessRate(executionLog: ProcessExecutionLog): Double
}
