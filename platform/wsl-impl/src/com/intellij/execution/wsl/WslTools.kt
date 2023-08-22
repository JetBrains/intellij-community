package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ProcessExistedNotZeroException
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

internal fun AbstractWslDistribution.getWslPath(path: Path): String = getWslPath(path.toString())
                                                                      ?: throw Exception("Can't access from Linux: $path")

internal fun AbstractWslDistribution.getTool(toolName: String, vararg arguments: String): GeneralCommandLine {
  val toolOnWindows = PathManager.findBinFileWithException(toolName)
  val toolOnLinux = getWslPath(toolOnWindows)
  try {
    runCommand("test", "-x", toolOnLinux)
  }
  catch (e: ProcessExistedNotZeroException) {
    throw Exception("File is not executable: $toolOnLinux ($toolOnWindows)", e)
  }
  return createWslCommandLine(toolOnLinux, *arguments)
}