// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.intellij.agent.workbench.cli.resolveExecutableFromPathOrLocalBin
import com.intellij.agent.workbench.cli.resolveExecutableOrDefaultViaTerminalResolver as resolveCliExecutableOrDefaultViaTerminalResolver
import com.intellij.agent.workbench.cli.resolveExecutableViaTerminalResolver as findCliExecutableViaTerminalResolver
import com.intellij.agent.workbench.cli.terminalAgentBinaryNameOrDefault
import com.intellij.util.SystemProperties
import java.nio.file.Path

private const val CODEX_MISSING_TERMINAL_AGENT_MESSAGE: String =
  "Codex terminal agent extension is not registered; falling back to local PATH lookup"

object CodexCliUtils {
  const val CODEX_COMMAND: String = "codex"
  const val CODEX_TERMINAL_AGENT_KEY: String = "codex"

  private const val CODEX_HOME_ENV: String = "CODEX_HOME"
  private const val CODEX_CONFIG_FILE_NAME: String = "config.toml"

  /**
   * Delegates to the shared terminal-agent resolver
   * (`TerminalAgentResolver`). This consults the same PATH + known-location pipeline that powers
   * the terminal's "Run AI agent" gutter, ensuring agent-workbench launches and terminal launches
   * pick the same `codex` binary. Returns `null` when the terminal extension does not surface a
   * Codex agent (e.g. an unusual test environment) or when the binary cannot be located at all.
   */
  suspend fun findExecutableViaTerminalResolver(): String? {
    return findCliExecutableViaTerminalResolver(
      terminalAgentKey = CODEX_TERMINAL_AGENT_KEY,
      fallbackResolver = ::findExecutable,
      missingAgentMessage = CODEX_MISSING_TERMINAL_AGENT_MESSAGE,
    )
  }

  /**
   * Suspend variant returning either the absolute path to the `codex` binary or [CODEX_COMMAND]
   * as a fallback. See [findExecutableViaTerminalResolver] for the rationale; the bare-command
   * fallback keeps the existing `cliMissingMessageKey` UI guard in charge of explaining the
   * missing CLI when nothing can be resolved.
   */
  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    resolveCliExecutableOrDefaultViaTerminalResolver(
      defaultCommand = CODEX_COMMAND,
      terminalAgentKey = CODEX_TERMINAL_AGENT_KEY,
      fallbackResolver = ::findExecutable,
      missingAgentMessage = CODEX_MISSING_TERMINAL_AGENT_MESSAGE,
    )

  fun findExecutable(): String? {
    return resolveExecutableFromPathOrLocalBin(CODEX_COMMAND)
  }

  fun codexConfigPath(): Path = codexHomePath().resolve(CODEX_CONFIG_FILE_NAME)

  fun codexHomePath(
    environment: Map<String, String> = System.getenv(),
    userHomePath: Path = Path.of(SystemProperties.getUserHome()),
  ): Path {
    val codexHome = environment[CODEX_HOME_ENV]?.takeIf { it.isNotBlank() }
    return codexHome?.let(Path::of) ?: userHomePath.resolve(".${codexBinaryName()}")
  }

  private fun codexBinaryName(): String = terminalAgentBinaryNameOrDefault(CODEX_TERMINAL_AGENT_KEY, CODEX_COMMAND)
}