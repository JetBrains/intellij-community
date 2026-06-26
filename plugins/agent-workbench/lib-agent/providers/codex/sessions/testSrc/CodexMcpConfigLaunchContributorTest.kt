// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class CodexMcpConfigLaunchContributorTest {
  @Test
  fun disablesLegacyFilteredAndStaleIdeMcpServersAndAddsLaunchScopedAwbServer(): Unit = runBlocking {
    val contributor = contributor(
      existingMcpServers = listOf(
        CodexMcpServerConfig(name = "ijproxy", url = null, projectPath = null, enabled = true),
        CodexMcpServerConfig(name = "ij-proxy", url = null, projectPath = null, enabled = true),
        CodexMcpServerConfig(name = "ij", url = OLD_MCP_URL, projectPath = PROJECT_PATH, enabled = true),
        CodexMcpServerConfig(name = "ij.container", url = MCP_URL, projectPath = OTHER_PROJECT_PATH, enabled = true),
        CodexMcpServerConfig(name = "external-http", url = EXTERNAL_MCP_URL, projectPath = null, enabled = true),
        CodexMcpServerConfig(name = "stdio", url = null, projectPath = null, enabled = true),
        CodexMcpServerConfig(name = "disabled", url = OLD_MCP_URL, projectPath = PROJECT_PATH, enabled = false),
      )
    )

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex")),
    )

    assertThat(launchSpec.command).containsExactlyElementsOf(listOf("codex") + EXPECTED_MCP_ARGS_WITH_FILTERED_DISABLES)
  }

  @Test
  fun disablesLegacyFilteredServersButDoesNotAddAwbServerWhenIdeaUrlMatches(): Unit = runBlocking {
    val contributor = contributor(
      existingMcpServers = listOf(
        CodexMcpServerConfig(name = "ijproxy", url = null, projectPath = null, enabled = true),
        CodexMcpServerConfig(name = "idea", url = MCP_URL, projectPath = null, enabled = true),
      )
    )

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex")),
    )

    assertThat(launchSpec.command).containsExactlyElementsOf(
      listOf("codex", "-c", "mcp_servers.ijproxy.enabled=false")
    )
  }

  @Test
  fun contributesOnlyAwbServerWhenLegacyServerNamesAreAbsent(): Unit = runBlocking {
    val contributor = contributor(existingMcpServers = emptyList())

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex")),
    )

    assertThat(launchSpec.command).containsExactlyElementsOf(listOf("codex") + EXPECTED_AWB_MCP_ARGS)
  }

  @Test
  fun insertsMcpConfigOverridesBeforeResumeCommand(): Unit = runBlocking {
    val contributor = contributor(existingMcpServers = FILTERED_MCP_SERVERS)

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = "thread-1",
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "--yolo", "resume", "--remote", REMOTE_URL, "thread-1")),
    )

    assertThat(launchSpec.command).containsExactlyElementsOf(
      listOf("codex", "--yolo") + EXPECTED_MCP_ARGS_WITH_FILTERED_DISABLES + listOf("resume", "--remote", REMOTE_URL, "thread-1")
    )
  }

  @Test
  fun insertsMcpConfigOverridesBeforeStartupPromptSeparator(): Unit = runBlocking {
    val contributor = contributor(existingMcpServers = FILTERED_MCP_SERVERS)

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "--", "Refactor this")),
    )

    assertThat(launchSpec.command).containsExactlyElementsOf(
      listOf("codex") + EXPECTED_MCP_ARGS_WITH_FILTERED_DISABLES + listOf("--", "Refactor this")
    )
  }

  @Test
  fun leavesNonCodexLaunchesUnchanged(): Unit = runBlocking {
    val contributor = contributor(existingMcpServers = FILTERED_MCP_SERVERS)
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("claude"))

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("claude"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }

  @Test
  fun leavesCodexLaunchUnchangedWhenDirectHttpRegistryGateIsDisabled(): Unit = runBlocking {
    val contributor = contributor(isDirectHttpEnabled = false)
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex"))

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }

  @Test
  fun leavesCodexLaunchUnchangedWhenMcpUrlIsUnavailable(): Unit = runBlocking {
    val contributor = contributor(mcpUrl = null)
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex"))

    val launchSpec = contributor.contribute(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      sessionId = null,
      launchSpec = baseLaunchSpec,
    )

    assertThat(launchSpec).isSameAs(baseLaunchSpec)
  }

  @Test
  fun readsCodexMcpServersFromTomlConfig(@TempDir tempDir: Path) {
    val configFile = tempDir.resolve("config.toml")
    configFile.writeText(
      """
        [mcp_servers.ij]
        url = "$OLD_MCP_URL"
        http_headers = { IJ_MCP_SERVER_PROJECT_PATH = "$PROJECT_PATH" }

        [mcp_servers."ij.container"]
        serverUrl = "$MCP_URL"
        headers = { IJ_MCP_SERVER_PROJECT_PATH = "$OTHER_PROJECT_PATH" }

        [mcp_servers.stdio]
        command = "bun"
        args = ["x"]

        [mcp_servers.disabled]
        enabled = false
        url = "$OLD_MCP_URL"
      """.trimIndent()
    )

    assertThat(readCodexMcpServers(configFile)).containsExactly(
      CodexMcpServerConfig(name = "ij", url = OLD_MCP_URL, projectPath = PROJECT_PATH, enabled = true),
      CodexMcpServerConfig(name = "ij.container", url = MCP_URL, projectPath = OTHER_PROJECT_PATH, enabled = true),
      CodexMcpServerConfig(name = "stdio", url = null, projectPath = null, enabled = true),
      CodexMcpServerConfig(name = "disabled", url = OLD_MCP_URL, projectPath = null, enabled = false),
    )
  }

  @Test
  fun readsCodexMcpServersFromUserAndProjectTomlConfigs(@TempDir tempDir: Path) {
    val userConfigFile = tempDir.resolve("user-config.toml")
    userConfigFile.writeText(
      """
        [mcp_servers.idea]
        url = "$OLD_MCP_URL"

        [mcp_servers.userOnly]
        command = "user-server"
      """.trimIndent()
    )
    val projectConfigFile = tempDir.resolve("project-config.toml")
    projectConfigFile.writeText(
      """
        [mcp_servers.idea]
        url = "$MCP_URL"

        [mcp_servers.ijproxy]
        command = "project-server"
      """.trimIndent()
    )

    assertThat(readCodexMcpServers(listOf(userConfigFile, projectConfigFile))).containsExactly(
      CodexMcpServerConfig(name = "idea", url = MCP_URL, projectPath = null, enabled = true),
      CodexMcpServerConfig(name = "userOnly", url = null, projectPath = null, enabled = true),
      CodexMcpServerConfig(name = "ijproxy", url = null, projectPath = null, enabled = true),
    )
  }

  @Test
  fun codexHomePathUsesCodexCommandFallbackWhenTerminalAgentLookupIsUnavailable() {
    val userHomePath = Path.of("/users/me")

    assertThat(CodexCliUtils.codexHomePath(environment = emptyMap(), userHomePath = userHomePath))
      .isEqualTo(userHomePath.resolve(".${CodexCliUtils.CODEX_COMMAND}"))
    assertThat(CodexCliUtils.codexHomePath(environment = mapOf("CODEX_HOME" to "/tmp/codex-home"), userHomePath = userHomePath))
      .isEqualTo(Path.of("/tmp/codex-home"))
  }

  private fun contributor(
    isDirectHttpEnabled: Boolean = true,
    mcpUrl: String? = MCP_URL,
    existingMcpServers: List<CodexMcpServerConfig> = emptyList(),
  ): CodexMcpConfigLaunchContributor {
    return CodexMcpConfigLaunchContributor(
      isDirectHttpEnabled = { isDirectHttpEnabled },
      mcpUrlResolver = { mcpUrl },
      existingMcpServersResolver = { _ -> existingMcpServers },
    )
  }
}

private const val PROJECT_PATH: String = "/work/project"
private const val OTHER_PROJECT_PATH: String = "/other/project"
private const val MCP_URL: String = "http://127.0.0.1:64342/stream"
private const val OLD_MCP_URL: String = "http://127.0.0.1:65432/stream"
private const val EXTERNAL_MCP_URL: String = "https://example.test/mcp"

private val FILTERED_MCP_SERVERS: List<CodexMcpServerConfig> = listOf(
  CodexMcpServerConfig(name = "ijproxy", url = null, projectPath = null, enabled = true),
  CodexMcpServerConfig(name = "ij-proxy", url = null, projectPath = null, enabled = true),
  CodexMcpServerConfig(name = "ij", url = OLD_MCP_URL, projectPath = PROJECT_PATH, enabled = true),
  CodexMcpServerConfig(name = "ij.container", url = MCP_URL, projectPath = OTHER_PROJECT_PATH, enabled = true),
)

private val EXPECTED_FILTERED_DISABLE_MCP_ARGS: List<String> = listOf(
  "-c", "mcp_servers.ijproxy.enabled=false",
  "-c", "mcp_servers.ij-proxy.enabled=false",
  "-c", "mcp_servers.ij.enabled=false",
  "-c", "mcp_servers.\"ij.container\".enabled=false",
)

private val EXPECTED_AWB_MCP_ARGS: List<String> = listOf(
  "-c", "mcp_servers.awb_idea.url=\"$MCP_URL\"",
  "-c", "mcp_servers.awb_idea.http_headers.IJ_MCP_SERVER_PROJECT_PATH=\"$PROJECT_PATH\"",
)

private val EXPECTED_MCP_ARGS_WITH_FILTERED_DISABLES: List<String> = EXPECTED_FILTERED_DISABLE_MCP_ARGS + EXPECTED_AWB_MCP_ARGS
