package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ProcessExistedNotZeroException

internal fun AbstractWslDistribution.getTool(toolName: String, vararg arguments: String): GeneralCommandLine {
  val toolOnLinux = getToolLinuxPath(toolName)
  try {
    runCommand("test", "-x", toolOnLinux)
  }
  catch (e: ProcessExistedNotZeroException) {
    throw Exception("File is not executable: $toolOnLinux", e)
  }
  return createWslCommandLine(toolOnLinux, *arguments)
}