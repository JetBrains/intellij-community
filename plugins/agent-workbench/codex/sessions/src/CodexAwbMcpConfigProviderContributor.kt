// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.sessions.core.launch.AwbMcpConfigProviderContributor

/**
 * Codex's contribution to the AWB-managed MCP launch contract.
 *
 * Codex's CLI doesn't accept `--mcp-config <file> --strict-mcp-config` semantics
 * compatible with Claude's, so this contributor inherits the default null `contribute`
 * and AWB falls through to the user's checked-in `.mcp.json` for Codex launches.
 *
 * What Codex *does* contribute is its bridge name [SERVER_NAME], so when **other**
 * providers (e.g. Claude) build a merged config that drops competing bridges, the
 * Codex entry from the user's `.mcp.json` is dropped too — preventing the merged
 * file from exposing two sets of IDE tools to the spawned agent.
 */
internal class CodexAwbMcpConfigProviderContributor : AwbMcpConfigProviderContributor {
  override val filteredServerNames: Set<String> = setOf(SERVER_NAME)

  private companion object {
    /** Codex CLI's stdio bridge to the IDE — same tool names as Claude's `ij-container`. */
    const val SERVER_NAME = "mcp-codex"
  }
}
