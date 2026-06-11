// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.util

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path

const val AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY: String = "agent.workbench.sessions.jbcentral.path"

object JbCentralCliSupport {
  private const val JBCENTRAL_COMMAND: String = "jbcentral"
  private const val LEGACY_COMMAND: String = "wire"

  fun findExecutable(): String? {
    val configuredPath = System.getProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)?.trim().orEmpty()
    if (configuredPath.isNotEmpty()) {
      return resolveConfiguredExecutable(configuredPath)
    }

    findExecutableOnPath()?.let { return it }

    val homeDir = SystemProperties.getUserHome().takeIf(String::isNotBlank) ?: return null
    for (candidate in fallbackCandidates(homeDir)) {
      if (Files.isRegularFile(candidate)) {
        return candidate.toAbsolutePath().toString()
      }
    }
    return null
  }

  fun isAvailable(): Boolean = findExecutable() != null

  private fun resolveConfiguredExecutable(configuredPath: String): String? {
    val path = Path.of(configuredPath).toAbsolutePath()
    return path.takeIf(Files::isRegularFile)?.toString()
  }

  private fun findExecutableOnPath(): String? {
    JbCentralCliSupportTestHook.pathLookupOverride()?.let { override ->
      return override()
    }
    for (command in commandCandidates()) {
      PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(command)?.absolutePath?.let { return it }
    }
    return null
  }

  private fun fallbackCandidates(homeDir: String): List<Path> {
    val installDir = Path.of(homeDir, ".local", "bin")
    return fallbackExecutableNames().map { executableName ->
      installDir.resolve(executableName)
    }
  }

  private fun commandCandidates(): List<String> = listOf(JBCENTRAL_COMMAND, LEGACY_COMMAND)

  private fun fallbackExecutableNames(): List<String> {
    return if (SystemInfoRt.isWindows) {
      listOf("$JBCENTRAL_COMMAND.exe", "$LEGACY_COMMAND.cmd", "$LEGACY_COMMAND.exe")
    }
    else {
      listOf(JBCENTRAL_COMMAND, LEGACY_COMMAND)
    }
  }
}

object JbCentralCliSupportTestHook {
  @Volatile
  private var pathLookupOverride: (() -> String?)? = null

  fun pathLookupOverride(): (() -> String?)? = pathLookupOverride

  @TestOnly
  fun replacePathLookupForTest(pathLookup: (() -> String?)?): (() -> String?)? {
    val previous = pathLookupOverride
    pathLookupOverride = pathLookup
    return previous
  }
}
