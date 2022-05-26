package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

internal fun AbstractWslDistribution.getWslPath(path: Path): String = getWslPath(path.toString())
                                                                      ?: throw Exception("Can't access from Linux: $path")

internal fun AbstractWslDistribution.getTool(toolName: String, vararg arguments: String): GeneralCommandLine =
  createWslCommandLine(getWslPath(PathManager.findBinFileWithException(toolName)), *arguments)