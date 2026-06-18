// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.agent.workbench.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.agent.workbench.sessions.core.launch.McpStreamUrlProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Points the Junie CLI at the AWB-managed `.awb/` directory via `--mcp-location` for
 * both new and resumed launches.
 *
 * Junie's CLI accepts `--mcp-location <dir>` (a directory containing MCP config),
 * unlike Claude's `--mcp-config <file>`. So this contributor lives alongside the
 * Junie provider rather than going through `AwbMcpConfigProviderContributor`, which
 * is tailored to the file-based contract.
 *
 * When direct HTTP MCP is enabled, we regenerate `.awb/awb-mcp.json` for Junie and
 * launch with `--mcp-default-locations=false`. That prevents Junie from loading
 * legacy project/user IDE bridges separately while the generated file keeps the
 * user's non-legacy MCP servers.
 *
 * Absent the directory we leave the spec untouched and let Junie fall back to its
 * default discovery — the directory may legitimately not exist yet on a fresh
 * project that hasn't launched any AWB session.
 */
internal class JunieAwbMcpLocationContributor(
  private val isDirectHttpEnabled: () -> Boolean = AwbMcpConfigBuilder::isDirectHttpEnabled,
  private val mcpUrlResolver: () -> String? = McpStreamUrlProvider::resolve,
  private val userHomePathProvider: () -> Path = { Path.of(System.getProperty("user.home") ?: ".") },
  private val writeMergedConfigFile: (Path, String, Set<String>, List<Path>) -> Path =
    { projectPath, mcpUrl, serverNames, additionalMcpConfigFiles ->
      AwbMcpConfigBuilder.writeMergedConfigFile(projectPath, mcpUrl, serverNames, additionalMcpConfigFiles)
    },
) : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != AgentSessionProvider.JUNIE) return launchSpec
    val normalizedProjectPath = try {
      Path.of(normalizeAgentWorkbenchPath(projectPath))
    }
    catch (e: Exception) {
      LOG.debug("Cannot normalize projectPath for Junie --mcp-location: $projectPath", e)
      return launchSpec
    }
    val awbDir = normalizedProjectPath.resolve(AwbMcpConfigBuilder.AWB_DIR)
    if (isDirectHttpEnabled()) {
      val mcpUrl = mcpUrlResolver() ?: run {
        LOG.info("No MCP stream URL available for $projectPath; Junie launch will keep default MCP discovery")
        return contributeExistingAwbLocation(awbDir, launchSpec)
      }
      val configFile = writeMergedConfigFile(
        normalizedProjectPath,
        mcpUrl,
        SERVER_NAMES,
        junieMcpConfigFiles(normalizedProjectPath),
      )
      return launchSpec.copy(
        command = launchSpec.command + listOf(
          MCP_DEFAULT_LOCATIONS_DISABLED_FLAG,
          MCP_LOCATION_FLAG,
          configFile.parent.toString(),
        ),
        envVariables = launchSpec.envVariables + mapOf(
          AwbMcpConfigBuilder.PROJECT_PATH_ENV to normalizedProjectPath.toString(),
        ),
      )
    }
    return contributeExistingAwbLocation(awbDir, launchSpec)
  }

  private fun contributeExistingAwbLocation(
    awbDir: Path,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (!Files.isDirectory(awbDir)) return launchSpec
    return launchSpec.copy(
      command = launchSpec.command + listOf(MCP_LOCATION_FLAG, awbDir.toString()),
    )
  }

  private fun junieMcpConfigFiles(projectPath: Path): List<Path> {
    return listOf(
      userHomePathProvider().resolve(JUNIE_DIR).resolve(MCP_DIR).resolve(MCP_FILE),
      projectPath.resolve(JUNIE_DIR).resolve(MCP_DIR).resolve(MCP_FILE),
      projectPath.resolve(PROJECT_MCP_FILE),
    )
  }

  private companion object {
    val LOG = logger<JunieAwbMcpLocationContributor>()
    val SERVER_NAMES: Set<String> = setOf("ij-container", "ij")
    const val MCP_DEFAULT_LOCATIONS_DISABLED_FLAG = "--mcp-default-locations=false"
    const val MCP_LOCATION_FLAG = "--mcp-location"
    const val JUNIE_DIR = ".junie"
    const val MCP_DIR = "mcp"
    const val MCP_FILE = "mcp.json"
    const val PROJECT_MCP_FILE = ".mcp.json"
  }
}
