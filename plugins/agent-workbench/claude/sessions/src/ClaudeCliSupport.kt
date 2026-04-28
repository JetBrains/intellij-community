// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.terminal.backend.rpc.TerminalAgentResolutionContext
import com.intellij.terminal.backend.rpc.findTerminalAgentBinaryPath
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ClaudeCliSupportLogCategory>()

private class ClaudeCliSupportLogCategory

object ClaudeCliSupport {
  const val CLAUDE_COMMAND: String = "claude"
  const val CLAUDE_TERMINAL_AGENT_KEY: String = "claude_code"

  fun isAvailable(): Boolean = findExecutable() != null

  fun findExecutable(): String? {
    val inPath = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(CLAUDE_COMMAND)
    @Suppress("IO_FILE_USAGE")
    if (inPath != null) return inPath.toPath().toAbsolutePath().toString()

    val homeDir = System.getProperty("user.home") ?: return null
    val localBin = Path.of(homeDir, ".local", "bin", CLAUDE_COMMAND)
    if (Files.exists(localBin)) return localBin.toAbsolutePath().toString()

    return null
  }

  /**
   * Returns the absolute path to the `claude` binary if it can be located, or [CLAUDE_COMMAND] as a fallback.
   *
   * Passing the absolute path to the terminal avoids `PATH`-resolution surprises (e.g. agent-workbench
   * shim directories prepended to `PATH`, GUI-inherited environments missing the user's npm prefix, etc.)
   * — those manifest as the opaque pty4j message `Exec_tty error: Unknown reason` when the binary is
   * not on `PATH`. When resolution fails, callers still get the literal `claude` so the existing
   * `cliMissingMessageKey` UI guard remains in charge of explaining the missing CLI.
   */
  fun resolveExecutableOrDefault(): String = findExecutable() ?: CLAUDE_COMMAND

  /**
   * Suspend variant of [findExecutable] that delegates to the shared terminal-agent resolver
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
    val context = createLocalResolutionContext()
    return findTerminalAgentBinaryPath(claudeAgent, context)
  }

  /**
   * Suspend variant of [resolveExecutableOrDefault]. See [findExecutableViaTerminalResolver] for the
   * rationale; falls back to [CLAUDE_COMMAND] when no binary can be located so the existing
   * `cliMissingMessageKey` UI guard remains in charge of explaining the missing CLI.
   */
  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    findExecutableViaTerminalResolver() ?: CLAUDE_COMMAND

  fun buildNewSessionCommand(yolo: Boolean, executable: String = CLAUDE_COMMAND): List<String> =
    if (yolo) listOf(executable, "--dangerously-skip-permissions")
    else listOf(executable, PERMISSION_MODE_FLAG, PERMISSION_MODE_DEFAULT)

  fun buildResumeCommand(sessionId: String, executable: String = CLAUDE_COMMAND): List<String> =
    listOf(executable, "--resume", sessionId)
}

/**
 * Builds a [TerminalAgentResolutionContext] for the local execution environment without requiring a
 * [com.intellij.openapi.project.Project]. The descriptor that powers Claude session launches is registered
 * as an application-level extension and does not own a project, so we need this project-less form to
 * reach the same resolver pipeline as the project-aware overload.
 */
private suspend fun createLocalResolutionContext(): TerminalAgentResolutionContext {
  val eelApi = LocalEelDescriptor.toEelApi()
  val environment = if (eelApi.platform.isWindows) {
    try {
      eelApi.exec.environmentVariables().onlyActual(true).eelIt().await()
    }
    catch (ex: EelExecApi.EnvironmentVariablesException) {
      LOG.warn("Failed to fetch environment variables for Claude CLI resolution", ex)
      emptyMap()
    }
  }
  else {
    emptyMap()
  }
  return TerminalAgentResolutionContext(
    eelApi = eelApi,
    osFamily = LocalEelDescriptor.osFamily,
    environment = environment,
  )
}

internal const val PERMISSION_MODE_FLAG: String = "--permission-mode"
internal const val PERMISSION_MODE_DEFAULT: String = "default"
internal const val PERMISSION_MODE_PLAN: String = "plan"
