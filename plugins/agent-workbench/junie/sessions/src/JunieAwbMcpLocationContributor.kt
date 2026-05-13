// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.agent.workbench.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Points the Junie CLI at the AWB-managed `.awb/` directory via `--mcp-location` for
 * both new and resumed launches when `<projectPath>/.awb` exists on disk.
 *
 * Junie's CLI accepts `--mcp-location <dir>` (a directory containing MCP config),
 * unlike Claude's `--mcp-config <file>`. So this contributor lives alongside the
 * Junie provider rather than going through `AwbMcpConfigProviderContributor`, which
 * is tailored to the file-based contract.
 *
 * Absent the directory we leave the spec untouched and let Junie fall back to its
 * default discovery — the directory may legitimately not exist yet on a fresh
 * project that hasn't launched any AWB session.
 */
internal class JunieAwbMcpLocationContributor : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != AgentSessionProvider.JUNIE) return launchSpec
    val awbDir = try {
      Path.of(normalizeAgentWorkbenchPath(projectPath)).resolve(AwbMcpConfigBuilder.AWB_DIR)
    }
    catch (e: Exception) {
      LOG.debug("Cannot normalize projectPath for Junie --mcp-location: $projectPath", e)
      return launchSpec
    }
    if (!Files.isDirectory(awbDir)) return launchSpec
    return launchSpec.copy(
      command = launchSpec.command + listOf(MCP_LOCATION_FLAG, awbDir.toString()),
    )
  }

  private companion object {
    val LOG = logger<JunieAwbMcpLocationContributor>()
    const val MCP_LOCATION_FLAG = "--mcp-location"
  }
}
