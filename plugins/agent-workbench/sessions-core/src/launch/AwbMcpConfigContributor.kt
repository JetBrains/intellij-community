// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path

/**
 * Always-on launch contributor that regenerates the AWB-managed merged MCP config file
 * (`<projectPath>/.awb/awb-mcp.json`) and points the spawned CLI at it via
 * `--mcp-config`. Fires for **both** new sessions and resumed ones: a tab reopen after
 * IDE restart picks up the current IDE MCP URL, and a fresh prompt-launch wires the
 * file through too without going through the container launcher's direct call.
 *
 * Provider-agnostic: any AWB launch with a project that has an IDE serving an MCP URL
 * benefits, container or not. The underlying [AwbMcpConfigBuilder] gates on the
 * registry flag and the per-provider [AwbMcpConfigProviderContributor], so launches
 * for providers without file-based config support are no-ops.
 */
internal class AwbMcpConfigContributor : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val target = try {
      Path.of(normalizeAgentWorkbenchPath(projectPath))
    }
    catch (e: Exception) {
      LOG.debug("Cannot normalize projectPath for AwbMcpConfig launch: $projectPath", e)
      return launchSpec
    }
    val config = AwbMcpConfigBuilder.buildForLaunch(target, provider) ?: return launchSpec
    LOG.info(
      "Launch contributor wrote MCP config for ${provider.value}:${sessionId ?: "<new>"} → ${config.configFile}"
    )
    return launchSpec.copy(
      command = launchSpec.command + config.extraArgs,
      envVariables = launchSpec.envVariables + config.envVariables,
    )
  }

  companion object {
    private val LOG = logger<AwbMcpConfigContributor>()
  }
}
