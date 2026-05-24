// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfoRt
import java.nio.file.Files
import java.nio.file.Path

internal const val AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY: String = "agent.workbench.sessions.jbcentral.path"

object JbCentralQuotaCliSupport {
  private const val JBCENTRAL_COMMAND: String = "jbcentral"

  fun findExecutable(): String? {
    val configuredPath = System.getProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)?.trim().orEmpty()
    if (configuredPath.isNotEmpty()) {
      return Path.of(configuredPath).toAbsolutePath().toString()
    }

    PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(JBCENTRAL_COMMAND)?.absolutePath?.let { return it }

    val homeDir = System.getProperty("user.home") ?: return null
    for (candidate in fallbackCandidates(homeDir)) {
      if (Files.exists(candidate)) {
        return candidate.toAbsolutePath().toString()
      }
    }
    return null
  }

  fun isAvailable(): Boolean = findExecutable() != null

  private fun fallbackCandidates(homeDir: String): List<Path> {
    val executableName = if (SystemInfoRt.isWindows) "$JBCENTRAL_COMMAND.exe" else JBCENTRAL_COMMAND
    return listOf(
      Path.of(homeDir, ".local", "bin", executableName),
      Path.of(homeDir, "JetBrains", "central-cli", executableName),
    )
  }
}
