// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentResolver
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ClaudeCliSupportLogCategory>()

private class ClaudeCliSupportLogCategory

object ClaudeCliSupport {
  const val CLAUDE_COMMAND: String = "claude"
  const val CLAUDE_TERMINAL_AGENT_KEY: String = "claude_code"

  /**
   * Delegates to the shared terminal-agent resolver
   * (`TerminalAgentResolver`). This consults the same PATH + known-location pipeline that powers
   * the terminal's "Run AI agent" gutter, ensuring agent-workbench launches and terminal launches
   * pick the same `claude` binary. Returns `null` when the terminal extension does not surface a
   * Claude agent (e.g. an unusual test environment) or when the binary cannot be located at all.
   */
  suspend fun findExecutableViaTerminalResolver(): String? {
    val claudeAgent = TerminalAgent.findByKey(TerminalAgent.AgentKey(CLAUDE_TERMINAL_AGENT_KEY))
    if (claudeAgent == null) {
      LOG.warn("Claude terminal agent extension is not registered; falling back to local PATH lookup")
      return findExecutable()
    }
    val eelApi = LocalEelDescriptor.toEelApi()
    return TerminalAgentResolver.findBinaryPath(claudeAgent, eelApi)
  }

  /**
   * Returns the absolute path resolved via [findExecutableViaTerminalResolver], or [CLAUDE_COMMAND]
   * as a fallback when no binary can be located so the existing `cliMissingMessageKey` UI guard
   * remains in charge of explaining the missing CLI. Passing the absolute path to the terminal
   * avoids `PATH`-resolution surprises (e.g. agent-workbench shim directories prepended to `PATH`,
   * GUI-inherited environments missing the user's npm prefix, etc.) — those manifest as the opaque
   * pty4j message `Exec_tty error: Unknown reason` when the binary is not on `PATH`.
   */
  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    findExecutableViaTerminalResolver() ?: CLAUDE_COMMAND

  fun buildNewSessionCommand(
    yolo: Boolean,
    sessionId: String,
    executable: String = CLAUDE_COMMAND,
  ): List<String> = if (yolo) {
    listOf(executable, "--dangerously-skip-permissions", SESSION_ID_FLAG, sessionId)
  }
  else {
    listOf(executable, SESSION_ID_FLAG, sessionId)
  }

  fun buildResumeCommand(
    sessionId: String,
    yolo: Boolean = false,
    executable: String = CLAUDE_COMMAND,
  ): List<String> = if (yolo) {
    listOf(executable, "--resume", sessionId, "--dangerously-skip-permissions")
  }
  else {
    listOf(executable, "--resume", sessionId)
  }

  internal fun findExecutable(): String? {
    val inPath = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(CLAUDE_COMMAND)
    @Suppress("IO_FILE_USAGE")
    if (inPath != null) return inPath.toPath().toAbsolutePath().toString()

    val homeDir = System.getProperty("user.home") ?: return null
    val localBin = Path.of(homeDir, ".local", "bin", CLAUDE_COMMAND)
    if (Files.exists(localBin)) return localBin.toAbsolutePath().toString()

    return null
  }
}

internal const val PERMISSION_MODE_FLAG: String = "--permission-mode"
internal const val EFFORT_FLAG: String = "--effort"
internal const val PERMISSION_MODE_DEFAULT: String = "default"
internal const val PERMISSION_MODE_PLAN: String = "plan"
private const val SESSION_ID_FLAG: String = "--session-id"
