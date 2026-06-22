// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.core.io.ParsingMode
import com.electronwill.nightconfig.toml.TomlFormat
import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchContributor
import com.intellij.platform.ai.agent.sessions.core.launch.AwbMcpConfigBuilder
import com.intellij.platform.ai.agent.sessions.core.launch.McpStreamUrlProvider
import com.intellij.platform.ai.agent.sessions.core.launch.insertArgumentsBefore
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal class CodexMcpConfigLaunchContributor(
  private val isDirectHttpEnabled: () -> Boolean = AwbMcpConfigBuilder::isDirectHttpEnabled,
  private val mcpUrlResolver: () -> String? = McpStreamUrlProvider::resolve,
  private val existingMcpServersResolver: (Path) -> List<CodexMcpServerConfig> = ::readCodexMcpServersForLaunch,
) : AgentSessionLaunchContributor {
  override suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    if (provider != AgentSessionProvider.CODEX || !isDirectHttpEnabled()) return launchSpec
    val mcpUrl = mcpUrlResolver() ?: return launchSpec
    val normalizedProjectPath = normalizeAgentWorkbenchPathOrNull(projectPath) ?: return launchSpec
    return launchSpec.copy(
      command = insertCodexMcpConfigArgs(
        command = launchSpec.command,
        args = buildCodexMcpConfigArgs(mcpUrl, normalizedProjectPath, existingMcpServersResolver(Path.of(normalizedProjectPath))),
      ),
    )
  }
}

private fun buildCodexMcpConfigArgs(
  mcpUrl: String,
  projectPath: String,
  existingMcpServers: List<CodexMcpServerConfig>,
): List<String> {
  val args = mutableListOf<String>()
  val disabledServerNames = existingMcpServers
    .asSequence()
    .filter { it.name != AWB_CODEX_MCP_SERVER_NAME && it.shouldDisableForDirectHttp(mcpUrl, projectPath) }
    .mapTo(LinkedHashSet(), CodexMcpServerConfig::name)
  for (serverName in disabledServerNames) {
    args.addAll(listOf("-c", "mcp_servers.${tomlKeySegment(serverName)}.enabled=false"))
  }
  if (existingMcpServers.any { it.isCurrentIdeMcpServer(mcpUrl, projectPath) }) {
    return args
  }
  args.addAll(
    listOf(
      "-c", "mcp_servers.$AWB_CODEX_MCP_SERVER_NAME.url=${tomlString(mcpUrl)}",
      "-c", "mcp_servers.$AWB_CODEX_MCP_SERVER_NAME.http_headers.$PROJECT_PATH_HEADER_NAME=${tomlString(projectPath)}",
    )
  )
  return args
}

private fun insertCodexMcpConfigArgs(command: List<String>, args: List<String>): List<String> {
  return insertArgumentsBefore(command, args, beforeTokens = setOf("resume", "--"))
}

private fun tomlString(value: String): String {
  return buildString {
    append('"')
    for (char in value) {
      when (char) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\b' -> append("\\b")
        '\t' -> append("\\t")
        '\n' -> append("\\n")
        '\u000C' -> append("\\f")
        '\r' -> append("\\r")
        else -> append(char)
      }
    }
    append('"')
  }
}

private fun tomlKeySegment(value: String): String {
  return if (TOML_BARE_KEY_REGEX.matches(value)) value else tomlString(value)
}

private fun readCodexMcpServersForLaunch(projectPath: Path): List<CodexMcpServerConfig> {
  return readCodexMcpServers(listOf(CodexCliUtils.codexConfigPath(), projectPath.resolve(CODEX_PROJECT_CONFIG_PATH)))
}

internal fun readCodexMcpServers(configFiles: List<Path>): List<CodexMcpServerConfig> {
  val result = LinkedHashMap<String, CodexMcpServerConfig>()
  for (configFile in configFiles) {
    for (server in readCodexMcpServers(configFile)) {
      result[server.name] = server
    }
  }
  return result.values.toList()
}

internal fun readCodexMcpServers(configFile: Path): List<CodexMcpServerConfig> {
  if (!Files.isRegularFile(configFile)) return emptyList()
  return runCatching {
    val root = parseTomlConfig(configFile.readText())
    val servers = root.readConfig(MCP_SERVERS_KEY) ?: return@runCatching emptyList()
    val result = mutableListOf<CodexMcpServerConfig>()
    for (entry in servers.entrySet()) {
      val name = entry.key.trim()
      if (name.isEmpty()) continue

      val table = entry.getRawValue<Any>() as? UnmodifiableConfig ?: continue
      val headers = table.readConfig("http_headers") ?: table.readConfig("headers")
      result += CodexMcpServerConfig(
        name = name,
        url = table.readString("url") ?: table.readString("serverUrl"),
        projectPath = headers?.readString(PROJECT_PATH_HEADER_NAME),
        enabled = table.readBoolean("enabled") ?: true,
      )
    }
    result
  }.getOrDefault(emptyList())
}

private fun parseTomlConfig(content: String): CommentedConfig {
  val config = TomlFormat.newConfig { LinkedHashMap<String, Any>() }
  TomlFormat.instance().createParser().parse(content, config, ParsingMode.REPLACE)
  return config
}

private fun CodexMcpServerConfig.shouldDisableForDirectHttp(currentMcpUrl: String, currentProjectPath: String): Boolean {
  return enabled && (name in AwbMcpConfigBuilder.LEGACY_FILTERED_NAMES || isStaleIdeMcpServer(currentMcpUrl, currentProjectPath))
}

private fun CodexMcpServerConfig.isCurrentIdeMcpServer(currentMcpUrl: String, currentProjectPath: String): Boolean {
  if (!enabled || name in AwbMcpConfigBuilder.LEGACY_FILTERED_NAMES) return false
  if (url != currentMcpUrl) return false
  if (!currentMcpUrl.isLocalIdeMcpUrl()) return false
  val normalizedServerProjectPath = projectPath?.let(::normalizeAgentWorkbenchPathOrNull)
  return projectPath == null || normalizedServerProjectPath == currentProjectPath
}

private fun CodexMcpServerConfig.isStaleIdeMcpServer(currentMcpUrl: String, currentProjectPath: String): Boolean {
  val serverUrl = url ?: return false
  val normalizedServerProjectPath = projectPath?.let(::normalizeAgentWorkbenchPathOrNull)
  if (projectPath != null && normalizedServerProjectPath != currentProjectPath) return true
  if (!serverUrl.isLocalIdeMcpUrl()) return false
  return serverUrl != currentMcpUrl
}

private fun String.isLocalIdeMcpUrl(): Boolean {
  val uri = runCatching { URI(this) }.getOrNull() ?: return false
  if (uri.scheme != "http" && uri.scheme != "https") return false
  if (uri.path != "/stream" && uri.path != "/sse") return false
  val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
  return host == "localhost" || host == "127.0.0.1" || host == "::1"
}

private fun UnmodifiableConfig.readConfig(key: String): UnmodifiableConfig? =
  getRaw<Any>(listOf(key)) as? UnmodifiableConfig

private fun UnmodifiableConfig.readString(key: String): String? =
  getRaw<Any>(listOf(key)) as? String

private fun UnmodifiableConfig.readBoolean(key: String): Boolean? =
  getRaw<Any>(listOf(key)) as? Boolean

internal data class CodexMcpServerConfig(
  val name: String,
  val url: String?,
  val projectPath: String?,
  val enabled: Boolean,
)

private const val AWB_CODEX_MCP_SERVER_NAME: String = "awb_idea"
private const val CODEX_PROJECT_CONFIG_PATH: String = ".codex/config.toml"
private const val MCP_SERVERS_KEY: String = "mcp_servers"
private const val PROJECT_PATH_HEADER_NAME: String = "IJ_MCP_SERVER_PROJECT_PATH"

private val TOML_BARE_KEY_REGEX: Regex = Regex("[A-Za-z0-9_-]+")
