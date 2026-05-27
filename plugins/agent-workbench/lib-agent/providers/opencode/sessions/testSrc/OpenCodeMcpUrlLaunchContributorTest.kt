// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenCodeMcpUrlLaunchContributorTest {
  @Test
  fun contributesActualMcpUrlEnvironmentForOpenCodeLaunches(): Unit = runBlocking {
    val contributor = OpenCodeMcpUrlLaunchContributor(
      mcpUrlResolver = { "http://127.0.0.1:63342/api/mcp" },
    )

    val launchSpec = contributor.contribute(
      projectPath = "/work/project",
      projectDirectory = null,
      provider = AgentSessionProvider.from("opencode"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("opencode")),
    )

    assertThat(launchSpec.envVariables).containsEntry(OPENCODE_MCP_URL_ENVIRONMENT_VARIABLE, "http://127.0.0.1:63342/api/mcp")
    assertThat(launchSpec.envVariables).containsEntry(AwbMcpConfigBuilder.PROJECT_PATH_ENV, "/work/project")
  }

  @Test
  fun contributesProjectDirectoryForBazelProjectIdentity(): Unit = runBlocking {
    val contributor = OpenCodeMcpUrlLaunchContributor(
      mcpUrlResolver = { "http://127.0.0.1:63342/api/mcp" },
    )

    val launchSpec = contributor.contribute(
      projectPath = "/work/project/toolbox/toolbox.bazelproject",
      projectDirectory = "/work/project",
      provider = AgentSessionProvider.from("opencode"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("opencode")),
    )

    assertThat(launchSpec.envVariables).containsEntry(AwbMcpConfigBuilder.PROJECT_PATH_ENV, "/work/project")
  }

  @Test
  fun preservesExistingLaunchEnvironmentVariables(): Unit = runBlocking {
    val contributor = OpenCodeMcpUrlLaunchContributor(
      mcpUrlResolver = { "http://127.0.0.1:63342/api/mcp" },
    )

    val launchSpec = contributor.contribute(
      projectPath = "/work/project",
      projectDirectory = null,
      provider = AgentSessionProvider.from("opencode"),
      sessionId = "thread-1",
      launchSpec = AgentSessionTerminalLaunchSpec(
        command = listOf("opencode"),
        envVariables = mapOf("EXISTING" to "1"),
      ),
    )

    assertThat(launchSpec.envVariables).containsEntry("EXISTING", "1")
    assertThat(launchSpec.envVariables).containsEntry(OPENCODE_MCP_URL_ENVIRONMENT_VARIABLE, "http://127.0.0.1:63342/api/mcp")
  }

  @Test
  fun leavesNonOpenCodeLaunchesUnchanged(): Unit = runBlocking {
    val contributor = OpenCodeMcpUrlLaunchContributor(
      mcpUrlResolver = { "http://127.0.0.1:63342/api/mcp" },
    )
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex"))

    val launchSpec = contributor.contribute(
      projectPath = "/work/project",
      projectDirectory = null,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }

  @Test
  fun leavesOpenCodeLaunchUnchangedWhenMcpUrlIsUnavailable(): Unit = runBlocking {
    val contributor = OpenCodeMcpUrlLaunchContributor(mcpUrlResolver = { null })
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("opencode"))

    val launchSpec = contributor.contribute(
      projectPath = "/work/project",
      projectDirectory = null,
      provider = AgentSessionProvider.from("opencode"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }
}
