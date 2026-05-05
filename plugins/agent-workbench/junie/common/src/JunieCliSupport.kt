// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.agent.workbench.junie.common

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentResolver
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<JunieCliSupportLogCategory>()

private class JunieCliSupportLogCategory

object JunieCliSupport {
  const val JUNIE_COMMAND: String = "junie"
  const val JUNIE_TERMINAL_AGENT_KEY: String = "junie"

  fun isAvailable(): Boolean = findExecutable() != null

  fun findExecutable(): String? {
    PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(JUNIE_COMMAND)?.absolutePath?.let { return it }

    val homeDir = System.getProperty("user.home") ?: return null
    for (executableName in localExecutableNames()) {
      val localBin = Path.of(homeDir, ".local", "bin", executableName)
      if (Files.exists(localBin)) return localBin.toAbsolutePath().toString()
    }
    return null
  }

  suspend fun findExecutableViaTerminalResolver(): String? {
    val junieAgent = TerminalAgent.findByKey(TerminalAgent.AgentKey(JUNIE_TERMINAL_AGENT_KEY))
    if (junieAgent == null) {
      LOG.warn("Junie terminal agent extension is not registered; falling back to local PATH lookup")
      return findExecutable()
    }
    val eelApi = LocalEelDescriptor.toEelApi()
    return TerminalAgentResolver.findBinaryPath(junieAgent, eelApi)
  }

  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    findExecutableViaTerminalResolver() ?: JUNIE_COMMAND

  fun buildNewSessionCommand(yolo: Boolean = false, executable: String = JUNIE_COMMAND): List<String> {
    return if (yolo) listOf(executable, SKIP_UPDATE_CHECK_FLAG, BRAVE_FLAG)
    else listOf(executable, SKIP_UPDATE_CHECK_FLAG)
  }

  fun buildResumeCommand(sessionId: String, executable: String = JUNIE_COMMAND): List<String> {
    return listOf(executable, SKIP_UPDATE_CHECK_FLAG, SESSION_ID_FLAG, sessionId)
  }

}

private fun localExecutableNames(): List<String> {
  return if (SystemInfoRt.isWindows) listOf("junie.bat") else listOf(JunieCliSupport.JUNIE_COMMAND)
}

private const val SKIP_UPDATE_CHECK_FLAG: String = "--skip-update-check"
const val BRAVE_FLAG: String = "--brave"
private const val SESSION_ID_FLAG: String = "--session-id"
