// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class JunieAwbMcpLocationContributorTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `direct http launch writes filtered merged mcp config and disables default locations`(): Unit = runBlocking {
    val projectPath = tempDir.resolve("project")
    val userHomePath = tempDir.resolve("home")
    Files.createDirectories(projectPath)
    writeMcpJson(
      userHomePath.resolve(".junie/mcp/mcp.json"),
      """
        {
          "mcpServers": {
            "Context7": {"command": "context7"},
            "shared": {"command": "user-shared"},
            "ijproxy": {"command": "legacy-user"}
          }
        }
      """.trimIndent(),
    )
    writeMcpJson(
      projectPath.resolve(".junie/mcp/mcp.json"),
      """
        {
          "mcpServers": {
            "Glean": {"command": "glean"},
            "shared": {"command": "project-shared"},
            "ij-proxy": {"command": "legacy-project"}
          }
        }
      """.trimIndent(),
    )
    writeMcpJson(
      projectPath.resolve(".mcp.json"),
      """
        {
          "mcpServers": {
            "ProjectTool": {"command": "project-tool"},
            "ij": {"url": "http://127.0.0.1:1/stream"}
          }
        }
      """.trimIndent(),
    )

    val launchSpec = contributor(userHomePath = userHomePath).contribute(
      projectPath = projectPath.toString(),
      provider = AgentSessionProvider.from("junie"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(
        command = listOf("junie", "--skip-update-check"),
        envVariables = mapOf("EXISTING" to "1"),
      ),
    )

    assertThat(launchSpec.command).containsExactly(
      "junie",
      "--skip-update-check",
      "--mcp-default-locations=false",
      "--mcp-location",
      projectPath.resolve(".awb").toString(),
    )
    assertThat(launchSpec.envVariables)
      .containsEntry("EXISTING", "1")
      .containsEntry(AwbMcpConfigBuilder.PROJECT_PATH_ENV, projectPath.toString())

    val generatedJson = Files.readString(AwbMcpConfigBuilder.configFilePath(projectPath))
    assertThat(generatedJson).contains(
      "\"Context7\"",
      "\"Glean\"",
      "\"ProjectTool\"",
      "project-tool",
      "\"shared\"",
      "project-shared",
      "\"ij-container\"",
      "\"ij\"",
      MCP_URL,
    )
    assertThat(generatedJson).doesNotContain(
      "user-shared",
      "ijproxy",
      "ij-proxy",
      "legacy-user",
      "legacy-project",
      "http://127.0.0.1:1/stream",
      "\"enabled\" : false",
      "\"enabled\":false",
    )
  }

  @Test
  fun `direct http launch falls back to existing awb location when mcp url is unavailable`(): Unit = runBlocking {
    val projectPath = tempDir.resolve("project")
    Files.createDirectories(projectPath.resolve(".awb"))
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("junie"))

    val launchSpec = contributor(mcpUrl = null).contribute(
      projectPath = projectPath.toString(),
      provider = AgentSessionProvider.from("junie"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec.command).containsExactly(
      "junie",
      "--mcp-location",
      projectPath.resolve(".awb").toString(),
    )
    assertThat(launchSpec.envVariables).isEmpty()
    assertThat(Files.exists(AwbMcpConfigBuilder.configFilePath(projectPath))).isFalse()
  }

  @Test
  fun `direct http disabled preserves existing awb location behavior`(): Unit = runBlocking {
    val projectPath = tempDir.resolve("project")
    Files.createDirectories(projectPath.resolve(".awb"))

    val launchSpec = contributor(isDirectHttpEnabled = false).contribute(
      projectPath = projectPath.toString(),
      provider = AgentSessionProvider.from("junie"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("junie")),
    )

    assertThat(launchSpec.command).containsExactly(
      "junie",
      "--mcp-location",
      projectPath.resolve(".awb").toString(),
    )
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun `non Junie launch is unchanged`(): Unit = runBlocking {
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("claude"))

    val launchSpec = contributor().contribute(
      projectPath = tempDir.resolve("project").toString(),
      provider = AgentSessionProvider.from("claude"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }

  private fun contributor(
    isDirectHttpEnabled: Boolean = true,
    mcpUrl: String? = MCP_URL,
    userHomePath: Path = tempDir.resolve("home"),
  ): JunieAwbMcpLocationContributor {
    return JunieAwbMcpLocationContributor(
      isDirectHttpEnabled = { isDirectHttpEnabled },
      mcpUrlResolver = { mcpUrl },
      userHomePathProvider = { userHomePath },
    )
  }
}

private fun writeMcpJson(path: Path, content: String) {
  Files.createDirectories(path.parent)
  Files.writeString(path, content)
}

private const val MCP_URL: String = "http://127.0.0.1:64342/stream"
