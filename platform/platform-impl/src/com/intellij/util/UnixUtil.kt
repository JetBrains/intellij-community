// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus

private const val COMMAND_LINE_TIMEOUT = 1000
private const val GLIBC_LIBRARY_NAME = "GLIBC"

@ApiStatus.Internal
object UnixUtil {
  private val LOG = Logger.getInstance(UnixUtil::class.java.name)

  @JvmStatic
  fun getGlibcVersion(): Double? {
    check(SystemInfo.isLinux) { "glibc version is only supported on Linux" }
    val commandLine = GeneralCommandLine("ldd", "--version")
    val output = ExecUtil.execAndGetOutput(commandLine, COMMAND_LINE_TIMEOUT)
    if (output.exitCode != 0) {
      LOG.warn("Failed to execute ${commandLine.commandLineString} exit code ${output.exitCode}")
      return null
    }
    val outputStr = output.stdout
    if (!outputStr.contains(GLIBC_LIBRARY_NAME, true)) {
      LOG.warn("Failed to find $GLIBC_LIBRARY_NAME in ${outputStr}")
      return null
    }
    val version = outputStr.split(" ").lastOrNull() ?: return null
    return version.toDoubleOrNull()
  }
}
