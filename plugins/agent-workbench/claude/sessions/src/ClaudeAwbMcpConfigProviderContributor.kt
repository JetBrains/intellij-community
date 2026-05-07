// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AwbMcpConfig
import com.intellij.agent.workbench.sessions.core.launch.AwbMcpConfigProviderContributor

/**
 * Claude Code's contribution to the AWB-managed MCP launch contract:
 *
 *  - Bridge names `ij-container` and `ij` — both registered as separate `mcpServers`
 *    entries pointing at the same IDE MCP URL. `ij-container` is the historical name
 *    AWB has emitted; `ij` is the shorter alias some Claude tooling and prompts refer
 *    to. Keeping both means agents addressing either name reach the same server, and
 *    user-supplied entries with either name are dropped during merge so our copies
 *    win.
 *  - `--mcp-config <file> --strict-mcp-config` CLI args. `--strict-mcp-config` makes
 *    Claude read **only** the file we passed (skipping the project's auto-loaded
 *    `.mcp.json`), so the merged file is the agent's full MCP world.
 */
internal class ClaudeAwbMcpConfigProviderContributor : AwbMcpConfigProviderContributor {
  override val filteredServerNames: Set<String> = SERVER_NAMES

  override fun contribute(provider: AgentSessionProvider): AwbMcpConfig? {
    if (provider != AgentSessionProvider.CLAUDE) return null
    return AwbMcpConfig(
      mcpServerNames = SERVER_NAMES,
      buildCliArgs = { configFile ->
        listOf("--mcp-config", configFile.toString(), "--strict-mcp-config")
      },
    )
  }

  private companion object {
    val SERVER_NAMES: Set<String> = setOf("ij-container", "ij")
  }
}
