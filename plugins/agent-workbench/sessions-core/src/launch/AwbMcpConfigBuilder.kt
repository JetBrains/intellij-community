// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultPrettyPrinter
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Builds and writes the AWB-managed merged MCP config file used by the agent CLI.
 *
 * Why a generated file: relying on Claude Code's `${VAR}` expansion in the auto-loaded
 * project-root `.mcp.json` proved unreliable in practice — the literal placeholder leaked
 * into the URL field and Claude failed with `"/" cannot be parsed as a URL against
 * "${JETBRAINS_IDE_MCP_URL}"`. With `--mcp-config <file> --strict-mcp-config`, Claude
 * reads only our file, with already-resolved literal values. No env-var expansion needed.
 *
 * The file lives at `<projectPath>/.awb/awb-mcp.json` and is **regenerated on every
 * AWB-driven launch** (new + resume), so each spawned CLI sees the IDE's *current*
 * streamable-HTTP MCP URL even after IDE restarts that reassign the port.
 *
 * The merge keeps the user's other MCP servers (Glean, Context7, etc.) usable: we copy
 * each entry from the project's `.mcp.json` verbatim into our file, dropping any name
 * registered by an [AwbMcpConfigProviderContributor]. The Jackson streaming parser uses
 * [JsonGenerator.copyCurrentStructure] to preserve unknown fields exactly.
 *
 * **Per-provider details live in [AwbMcpConfigProviderContributor] implementations**
 * (registered by claude/codex/junie modules), not here. This class only orchestrates:
 * registry gate → contributor lookup → file write → CLI args. Adding a new provider
 * doesn't require any changes to sessions-core.
 *
 * When the registry key `agent.workbench.mcp.use.direct.http` is OFF, no contributor
 * handles the active provider, or the local IDE doesn't expose an `mcpServerUrl`,
 * [buildForLaunch] returns `null` and callers fall back to whatever the user's
 * `.mcp.json` already does (e.g. ij-proxy stdio). This keeps the legacy path uninvolved.
 */
object AwbMcpConfigBuilder {
  private val LOG = logger<AwbMcpConfigBuilder>()
  private const val REGISTRY_KEY = "agent.workbench.mcp.use.direct.http"
  private const val USER_MCP_FILE = ".mcp.json"
  const val AWB_DIR: String = ".awb"
  private const val GENERATED_FILE = "awb-mcp.json"

  /**
   * Names that AWB-agnostic legacy proxies use in users' `.mcp.json`. Always filtered
   * out during merge — these aren't tied to any specific provider, so they don't
   * belong in a contributor.
   */
  private val LEGACY_FILTERED_NAMES = setOf("ijproxy", "ij-proxy")

  /**
   * Pins server-side project resolution to a specific project path. Read by
   * `McpProjectLocationInputs.resolveProject` to disambiguate when several IDE projects
   * are open against the same MCP server.
   */
  const val PROJECT_PATH_ENV: String = "JETBRAINS_MCP_PROJECT_PATH"

  data class LaunchConfig(
    /** CLI args to append to the agent command, e.g. `--mcp-config <file> --strict-mcp-config`. */
    val extraArgs: List<String>,
    /** Env variables for project disambiguation. */
    val envVariables: Map<String, String>,
    /** Generated config file path; written at this exact location. */
    val configFile: Path,
  )

  /**
   * Returns true when AWB should manage the agent's MCP config via the merged
   * `--mcp-config <file>` route for [provider]. Two gates:
   *  - The registry key `agent.workbench.mcp.use.direct.http` must be on.
   *  - Some [AwbMcpConfigProviderContributor] must accept [provider] (i.e. the provider's
   *    CLI is known to support a compatible `--mcp-config <file>` invocation).
   */
  fun isEnabled(provider: AgentSessionProvider): Boolean =
    Registry.`is`(REGISTRY_KEY, false) && findContribution(provider) != null

  /**
   * Returns a [LaunchConfig] when [isEnabled] holds for [provider] AND the local IDE
   * reports an `mcpServerUrl`. Returns `null` otherwise — the caller falls back to its
   * existing launch path (typically the user's checked-in `.mcp.json`).
   *
   * Uses [McpStreamUrlProvider.resolve] (the in-process `McpServerService` URL) rather
   * than parsing the per-PID discovery JSONs: AWB always launches from inside the IDE
   * that owns the project, so the local URL is by definition the correct one — and
   * reading our own discovery file would be wrong if it's stale or hasn't been written
   * yet during early startup.
   *
   * Side effect: writes `<projectPath>/.awb/awb-mcp.json` with the merged config and a
   * literal IDE MCP URL.
   */
  fun buildForLaunch(projectPath: Path, provider: AgentSessionProvider): LaunchConfig? {
    if (!Registry.`is`(REGISTRY_KEY, false)) return null
    val contribution = findContribution(provider) ?: return null
    val mcpUrl = McpStreamUrlProvider.resolve() ?: run {
      LOG.info("No MCP stream URL available for $projectPath; falling back to user's .mcp.json")
      return null
    }
    val configFile = writeMergedConfigFile(projectPath, mcpUrl, contribution.mcpServerNames)
    return LaunchConfig(
      extraArgs = contribution.buildCliArgs(configFile),
      envVariables = mapOf(PROJECT_PATH_ENV to projectPath.toString()),
      configFile = configFile,
    )
  }

  /**
   * Deterministic per-project config file path under `.awb/`.
   */
  fun configFilePath(projectPath: Path): Path =
    projectPath.resolve(AWB_DIR).resolve(GENERATED_FILE)

  /**
   * Writes the merged config to disk. Public so callers can pre-generate before
   * constructing a launch spec without going through [buildForLaunch].
   */
  fun writeMergedConfigFile(projectPath: Path, mcpUrl: String, ourServerNames: Set<String>): Path {
    val path = configFilePath(projectPath)
    Files.createDirectories(path.parent)
    Files.writeString(
      path,
      buildMergedConfigJson(projectPath, mcpUrl, ourServerNames),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
    )
    return path
  }

  private fun findContribution(provider: AgentSessionProvider): AwbMcpConfig? {
    for (contributor in AwbMcpConfigProviderContributor.EP.extensionList) {
      val contribution = contributor.contribute(provider) ?: continue
      return contribution
    }
    return null
  }

  /** Union of legacy proxy names and every contributor's declared bridge names. */
  private fun filteredNames(): Set<String> {
    val result = LEGACY_FILTERED_NAMES.toMutableSet()
    for (contributor in AwbMcpConfigProviderContributor.EP.extensionList) {
      result += contributor.filteredServerNames
    }
    return result
  }

  private fun buildMergedConfigJson(projectPath: Path, mcpUrl: String, ourServerNames: Set<String>): String {
    val filtered = filteredNames()
    val factory = JsonFactory()
    val out = StringWriter()
    factory.createJsonGenerator(out, DefaultPrettyPrinter()).use { gen ->
      gen.writeStartObject()
      gen.writeName("mcpServers")
      gen.writeStartObject()

      // Copy the user's other MCP servers verbatim, skipping filtered names.
      val userMcp = projectPath.resolve(USER_MCP_FILE)
      if (Files.isRegularFile(userMcp)) {
        try {
          copyUserMcpServers(factory, userMcp, gen, filtered)
        }
        catch (e: Exception) {
          LOG.warn("Failed to merge $userMcp; continuing with our entry only", e)
        }
      }

      // Our entries — written last, so name collisions in the user's file are
      // overwritten by Claude Code's last-key-wins JSON semantics if our skip list
      // ever misses one. All names point at the same IDE MCP URL (some agent tooling
      // expects specific bridge names; declaring multiple aliases keeps every variant
      // resolvable).
      for (name in ourServerNames) {
        gen.writeName(name)
        gen.writeStartObject()
        gen.writeStringProperty("type", "http")
        gen.writeStringProperty("url", mcpUrl)
        gen.writeEndObject()
      }

      gen.writeEndObject() // mcpServers
      gen.writeEndObject() // root
    }
    return out.toString()
  }

  private fun copyUserMcpServers(
    factory: JsonFactory,
    userMcp: Path,
    target: JsonGenerator,
    filtered: Set<String>,
  ) {
    factory.createJsonParser(Files.newBufferedReader(userMcp)).use { parser ->
      // Find the top-level "mcpServers" field.
      while (parser.nextToken() != null) {
        val name = parser.currentName()
        if (parser.currentToken() == JsonToken.PROPERTY_NAME && name == "mcpServers") {
          if (parser.nextToken() != JsonToken.START_OBJECT) return
          while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) continue
            val serverName = parser.currentName()
            parser.nextToken() // advance to value
            if (serverName in filtered) {
              parser.skipChildren()
              continue
            }
            target.writeName(serverName)
            target.copyCurrentStructure(parser)
          }
          return
        }
      }
    }
  }
}
