// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.opencode.sessions

import com.intellij.agent.workbench.cli.resolveExecutableOrDefaultViaTerminalResolver as resolveCliExecutableOrDefaultViaTerminalResolver
import com.intellij.agent.workbench.cli.resolveExecutableViaTerminalResolver as findCliExecutableViaTerminalResolver
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import javax.swing.Icon

internal object OpenCodeCliSupport {
  const val OPENCODE_COMMAND: String = "opencode"
  const val OPENCODE_TERMINAL_AGENT_KEY: String = "opencode"
  internal val resolverTerminalAgent: TerminalAgent = OpenCodeResolverTerminalAgent

  suspend fun findExecutableViaTerminalResolver(): String? = findCliExecutableViaTerminalResolver(resolverTerminalAgent)

  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    resolveCliExecutableOrDefaultViaTerminalResolver(OPENCODE_COMMAND, resolverTerminalAgent)
}

private object OpenCodeResolverTerminalAgent : TerminalAgent {
  override val agentKey: TerminalAgent.AgentKey = TerminalAgent.AgentKey(OpenCodeCliSupport.OPENCODE_TERMINAL_AGENT_KEY)
  override val displayName: String = "OpenCode"
  override val binaryName: String = OpenCodeCliSupport.OPENCODE_COMMAND
  override val posixKnownLocationCandidates: List<String> = listOf(
    $$"$HOME/.opencode/bin",
    $$"$HOME/.local/bin",
    "/opt/homebrew/bin",
    "/usr/local/bin",
  )
  override val windowsKnownLocationCandidates: List<String> = listOf(
    $$"$HOME\\AppData\\Roaming\\npm",
    $$"$HOME\\.local\\bin",
  )
  override val icon: Icon = AgentWorkbenchCommonIcons.Opencode
}
