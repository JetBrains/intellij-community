package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ProcessExistedNotZeroException
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

/**
 * throws exception instead of null
 */
internal fun AbstractWslDistribution.getWslPathSafe(path: Path): String = getWslPath(path)
                                                                          ?: throw Exception("Can't access from Linux: $path")

/**
 * How Linux can access tool from IJ "bin" folder
 */
internal fun AbstractWslDistribution.getToolLinuxPath(toolName: String): String =
  getWslPathSafe(PathManager.findBinFileWithException(toolName))

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