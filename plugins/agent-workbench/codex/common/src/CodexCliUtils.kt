// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

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

private val LOG = logger<CodexCliUtilsLogCategory>()

private class CodexCliUtilsLogCategory

object CodexCliUtils {
  const val CODEX_COMMAND: String = "codex"
  const val CODEX_TERMINAL_AGENT_KEY: String = "codex"

  fun findExecutable(): String? {
    return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(CODEX_COMMAND)?.absolutePath
  }

  /**
   * Suspend variant of [findExecutable] that delegates to the shared terminal-agent resolver
   * (`TerminalAgentResolver`). This consults the same PATH + known-location pipeline that powers
   * the terminal's "Run AI agent" gutter, ensuring agent-workbench launches and terminal launches
   * pick the same `codex` binary. Returns `null` when the terminal extension does not surface a
   * Codex agent (e.g. an unusual test environment) or when the binary cannot be located at all.
   */
  suspend fun findExecutableViaTerminalResolver(): String? {
    val codexAgent = TerminalAgent.findByKey(TerminalAgent.AgentKey(CODEX_TERMINAL_AGENT_KEY))
    if (codexAgent == null) {
      LOG.warn("Codex terminal agent extension is not registered; falling back to local PATH lookup")
      return findExecutable()
    }
    val context = createLocalResolutionContext()
    return findTerminalAgentBinaryPath(codexAgent, context)
  }

  /**
   * Suspend variant returning either the absolute path to the `codex` binary or [CODEX_COMMAND]
   * as a fallback. See [findExecutableViaTerminalResolver] for the rationale; the bare-command
   * fallback keeps the existing `cliMissingMessageKey` UI guard in charge of explaining the
   * missing CLI when nothing can be resolved.
   */
  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    findExecutableViaTerminalResolver() ?: CODEX_COMMAND
}

/**
 * Builds a [TerminalAgentResolutionContext] for the local execution environment without requiring a
 * [com.intellij.openapi.project.Project]. The descriptor that powers Codex session launches is registered
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
      LOG.warn("Failed to fetch environment variables for Codex CLI resolution", ex)
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
