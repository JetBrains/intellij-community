// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

/**
 * Per-provider plug-point for the AWB-managed MCP launch contract.
 *
 * Each provider module (claude, codex, junie, …) registers one of these from its own
 * plugin XML to declare:
 *
 *  - **Which `.mcp.json` server names belong to its IDE bridge.** The union across all
 *    contributors is dropped from the user's `.mcp.json` during merge so the AWB-managed
 *    file never carries stale bridge entries from a different provider's tooling.
 *  - **Whether AWB drives that provider via the file-based merged config**, by returning
 *    a non-null [contribute] for it. Only providers whose CLI accepts something
 *    compatible with `--mcp-config <file> --strict-mcp-config` should opt in; others
 *    (Codex's `apply` mode, agents that auto-discover `.mcp.json`) should leave
 *    [contribute] returning null and let AWB fall through to the user's checked-in
 *    `.mcp.json`.
 *
 * Keeping this knowledge in the provider modules — rather than hard-coding it in
 * [AwbMcpConfigBuilder] — means adding a new provider doesn't touch sessions-core at
 * all. Distinct from [AwbMcpConfigContributor], which is the single launch-time hook
 * that fires for both new and resumed sessions and orchestrates these per-provider
 * contributions into the actual launch spec.
 */
interface AwbMcpConfigProviderContributor {
  /**
   * Server names this contributor's bridge claims in users' `.mcp.json`. Always
   * filtered out during merge regardless of which provider is actively launching.
   * A provider may declare multiple names (e.g. legacy spellings).
   */
  val filteredServerNames: Set<String>

  /**
   * Returns the per-provider MCP config for [provider] when this contributor handles
   * it, or null otherwise. The non-null result tells [AwbMcpConfigBuilder] both that
   * it can build a merged config for this launch and how to wire the agent CLI to it.
   *
   * Default returns `null` for contributors that exist solely to register names in
   * [filteredServerNames] (so the user's `.mcp.json` entries with those names are
   * dropped during merge) without otherwise taking over the launch.
   */
  fun contribute(provider: AgentSessionProvider): AwbMcpConfig? = null

  companion object {
    val EP: ExtensionPointName<AwbMcpConfigProviderContributor> =
      ExtensionPointName("com.intellij.agent.workbench.mcpConfigProviderContributor")
  }
}

/**
 * What a single provider contributes to a launch, once
 * [AwbMcpConfigProviderContributor.contribute] has determined it handles the active
 * provider.
 */
data class AwbMcpConfig(
  /**
   * Names under which the IDE-bridge entry is written in the merged config file. All
   * entries point at the same IDE MCP URL — multiple names exist when an agent's
   * tooling expects a specific bridge name (e.g. Claude Code refers to
   * `ij`/`ij-container` interchangeably depending on the version). Each name
   * registered here should also appear in
   * [AwbMcpConfigProviderContributor.filteredServerNames] so a user-supplied entry of
   * the same name is dropped during merge — otherwise our always-last write would
   * still overlap with the user's entry's body.
   */
  val mcpServerNames: Set<String>,
  /**
   * CLI args to append to the agent command. Typical shape:
   * `["--mcp-config", configFile, "--strict-mcp-config"]`.
   */
  val buildCliArgs: (configFile: Path) -> List<String>,
)
