// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.platform.ai.agent.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.platform.ai.agent.sessions.core.launch.McpStreamUrlProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger

internal class OpenCodeMcpUrlLaunchContributor(
  private val mcpUrlResolver: () -> String? = McpStreamUrlProvider::resolve,
) : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    projectDirectory: String?,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != OPENCODE_AGENT_SESSION_PROVIDER) return launchSpec
    val mcpUrl = mcpUrlResolver() ?: run {
      LOG.info("No MCP stream URL available for $projectPath; OpenCode launch will not receive $OPENCODE_MCP_URL_ENVIRONMENT_VARIABLE")
      return launchSpec
    }
    val envVariables = LinkedHashMap(launchSpec.envVariables)
    envVariables[OPENCODE_MCP_URL_ENVIRONMENT_VARIABLE] = mcpUrl
    normalizeAgentWorkbenchPathOrNull(projectDirectory ?: projectPath)?.let { normalizedProjectPath ->
      envVariables[AwbMcpConfigBuilder.PROJECT_PATH_ENV] = normalizedProjectPath
    }
    return launchSpec.copy(envVariables = envVariables)
  }

  companion object {
    private val LOG = logger<OpenCodeMcpUrlLaunchContributor>()
  }
}
