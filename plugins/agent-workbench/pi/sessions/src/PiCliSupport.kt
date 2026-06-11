// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentResolver
import javax.swing.Icon

internal object PiCliSupport {
  const val PI_COMMAND: String = "pi"
  const val PI_TERMINAL_AGENT_KEY: String = "pi"
  internal val resolverTerminalAgent: TerminalAgent = PiResolverTerminalAgent

  suspend fun findExecutableViaTerminalResolver(): String? {
    val eelApi = LocalEelDescriptor.toEelApi()
    return TerminalAgentResolver.findBinaryPath(resolverTerminalAgent, eelApi)
  }

  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String = findExecutableViaTerminalResolver() ?: PI_COMMAND
}

private object PiResolverTerminalAgent : TerminalAgent {
  override val agentKey: TerminalAgent.AgentKey = TerminalAgent.AgentKey(PiCliSupport.PI_TERMINAL_AGENT_KEY)
  override val displayName: String = "Pi"
  override val binaryName: String = PiCliSupport.PI_COMMAND
  override val posixKnownLocationCandidates: List<String> = listOf(
    $$"$HOME/.local/bin",
    "/opt/homebrew/bin",
    "/usr/local/bin",
  )
  override val windowsKnownLocationCandidates: List<String> = listOf(
    $$"$HOME\\AppData\\Roaming\\npm",
    $$"$HOME\\.local\\bin",
  )
  override val icon: Icon? = null
}
