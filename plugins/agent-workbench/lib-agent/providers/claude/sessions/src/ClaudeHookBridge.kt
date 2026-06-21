// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-claude-hooks.spec.md

import com.intellij.agent.workbench.core.AgentThreadActivity
import com.intellij.agent.workbench.core.AgentThreadActivityReport
import com.intellij.agent.workbench.core.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.core.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.claude.common.CLAUDE_HOOK_PROJECT_MUTATING_TOOL_MATCHER
import com.intellij.agent.workbench.claude.common.CLAUDE_USER_INTERACTION_TOOL_MATCHER
import com.intellij.agent.workbench.claude.common.isClaudeHookProjectMutatingToolName
import com.intellij.agent.workbench.claude.common.isClaudeUserInteractionToolName
import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.DigestUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.ide.BuiltInServerManager
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

private val CLAUDE_HOOK_LOG = logger<ClaudeHookBridge>()

internal object ClaudeHookBridge {
  private val jsonFactory = JsonFactory()
  private val sessionIdsByToken = ConcurrentHashMap<String, String>()
  private val tokensBySessionId = ConcurrentHashMap<String, MutableSet<String>>()
  private val settingsPathsByToken = ConcurrentHashMap<String, Path>()
  private val hookUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  val updateEvents: Flow<AgentSessionSourceUpdateEvent> = hookUpdates

  fun createLaunchSettingsArgument(sessionId: String): String? {
    return createLaunchSettings(sessionId = sessionId)?.settingsPath
  }

  internal fun createLaunchSettings(
    sessionId: String,
    portProvider: () -> Int = { BuiltInServerManager.getInstance().waitForStart().port },
    settingsDirectoryProvider: () -> Path = { PathManager.getSystemDir().resolve("agent-workbench/claude-hooks") },
  ): ClaudeHookLaunchSettings? {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return null
    val token = DigestUtil.randomToken()
    sessionIdsByToken[token] = normalizedSessionId
    tokensBySessionId.computeIfAbsent(normalizedSessionId) { ConcurrentHashMap.newKeySet() }.add(token)

    try {
      val directory = settingsDirectoryProvider()
      Files.createDirectories(directory)
      val endpoint = "http://localhost:${portProvider()}/$CLAUDE_HOOK_ENDPOINT_PREFIX"
      val settingsPath = directory.resolve("settings-${token.toFileNameToken()}.json")
      Files.writeString(
        settingsPath,
        createClaudeHookSettingsText(endpoint = endpoint, token = token),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
      )
      settingsPathsByToken[token] = settingsPath
      CLAUDE_HOOK_LOG.debug { "Created Claude hook settings (sessionId=$normalizedSessionId, endpoint=$endpoint)" }
      return ClaudeHookLaunchSettings(
        endpoint = endpoint,
        token = token,
        settingsPath = settingsPath.toString(),
      )
    }
    catch (e: Exception) {
      removeToken(token)
      CLAUDE_HOOK_LOG.warn("Failed to create Claude hook settings", e)
      return null
    }
  }

  fun handleHookRequest(token: String?, content: String): ClaudeHookRequestResult {
    val normalizedToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: return ClaudeHookRequestResult.UNAUTHORIZED
    val expectedSessionId = sessionIdsByToken[normalizedToken] ?: return ClaudeHookRequestResult.UNAUTHORIZED
    val payload = parseHookPayload(content) ?: return ClaudeHookRequestResult.BAD_REQUEST
    val sessionId = payload.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return ClaudeHookRequestResult.BAD_REQUEST
    if (sessionId != expectedSessionId) return ClaudeHookRequestResult.UNAUTHORIZED

    val resolution = createHookUpdate(payload = payload, sessionId = sessionId)
    if (resolution is ClaudeHookUpdateResolution.Ignored) {
      CLAUDE_HOOK_LOG.debug {
        "Ignored Claude hook update " +
        "(sessionId=$sessionId, reason=${resolution.reason}, hookEvent=${payload.hookEventName}, tool=${payload.toolName}, cwd=${payload.cwd})"
      }
      return ClaudeHookRequestResult.ACCEPTED
    }

    val updateEvent = (resolution as ClaudeHookUpdateResolution.Emitted).updateEvent
    CLAUDE_HOOK_LOG.debug {
      val activityReport = updateEvent.activityUpdatesByThreadId[sessionId]?.activityReport
      "Accepted Claude hook update " +
      "(sessionId=$sessionId, hookEvent=${payload.hookEventName}, tool=${payload.toolName}, cwd=${payload.cwd}, " +
      "updateType=${updateEvent.type}, rowActivity=${activityReport?.rowActivity}, chromeActivity=${activityReport?.chromeActivity}, " +
      "mayHaveChangedProjectFiles=${updateEvent.mayHaveChangedProjectFiles}, changedProjectFilePaths=${updateEvent.changedProjectFilePaths?.size})"
    }
    hookUpdates.tryEmit(updateEvent)
    return ClaudeHookRequestResult.ACCEPTED
  }

  internal fun invalidateSession(sessionId: String) {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return
    val tokens = tokensBySessionId.remove(normalizedSessionId) ?: return
    for (token in tokens) {
      sessionIdsByToken.remove(token, normalizedSessionId)
      removeSettingsFile(token)
    }
  }

  private fun removeToken(token: String) {
    val sessionId = sessionIdsByToken.remove(token)
    removeSettingsFile(token)
    if (sessionId == null) return
    val tokens = tokensBySessionId[sessionId] ?: return
    tokens.remove(token)
    if (tokens.isEmpty()) {
      tokensBySessionId.remove(sessionId, tokens)
    }
  }

  private fun removeSettingsFile(token: String) {
    val settingsPath = settingsPathsByToken.remove(token) ?: return
    try {
      Files.deleteIfExists(settingsPath)
    }
    catch (e: Exception) {
      CLAUDE_HOOK_LOG.debug("Failed to delete Claude hook settings file $settingsPath", e)
    }
  }

  private fun parseHookPayload(content: String): ClaudeHookPayload? {
    return try {
      jsonFactory.createJsonParser(content).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        readHookPayload(parser)
      }
    }
    catch (e: Exception) {
      CLAUDE_HOOK_LOG.debug("Failed to parse Claude hook payload", e)
      null
    }
  }
}

internal data class ClaudeHookLaunchSettings(
  @JvmField val endpoint: String,
  @JvmField val token: String,
  @JvmField val settingsPath: String,
)

internal enum class ClaudeHookRequestResult {
  ACCEPTED,
  UNAUTHORIZED,
  BAD_REQUEST,
}

private data class ClaudeHookPayload(
  @JvmField val hookEventName: String? = null,
  @JvmField val sessionId: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val toolName: String? = null,
  @JvmField val toolInputProjectFilePaths: Set<String> = emptySet(),
)

internal const val CLAUDE_HOOK_ENDPOINT_PREFIX: String = "agent-workbench/claude/hook"

internal fun createClaudeHookSettingsText(endpoint: String, token: String): String {
  val writer = StringWriter()
  JsonFactory().createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    generator.writeName("hooks")
    generator.writeStartObject()
    generator.writeName("PreToolUse")
    generator.writeStartArray()
    generator.writeHookEntry(matcher = CLAUDE_USER_INTERACTION_TOOL_MATCHER, endpoint = endpoint, token = token)
    generator.writeEndArray()
    generator.writeName("PostToolUse")
    generator.writeStartArray()
    generator.writeHookEntry(matcher = CLAUDE_HOOK_PROJECT_MUTATING_TOOL_MATCHER, endpoint = endpoint, token = token)
    generator.writeEndArray()
    generator.writeEndObject()
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun JsonGenerator.writeHookEntry(matcher: String, endpoint: String, token: String) {
  writeStartObject()
  writeStringProperty("matcher", matcher)
  writeName("hooks")
  writeStartArray()
  writeStartObject()
  writeStringProperty("type", "http")
  writeStringProperty("url", endpoint)
  writeName("headers")
  writeStartObject()
  writeStringProperty("Authorization", "Bearer $token")
  writeEndObject()
  writeNumberProperty("timeout", CLAUDE_HOOK_HTTP_TIMEOUT_SECONDS)
  writeEndObject()
  writeEndArray()
  writeEndObject()
}

private fun readHookPayload(parser: JsonParser): ClaudeHookPayload {
  var hookEventName: String? = null
  var sessionId: String? = null
  var cwd: String? = null
  var toolName: String? = null
  val toolInputProjectFilePaths = LinkedHashSet<String>()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "hook_event_name", "hookEventName", "hook_event", "event" -> hookEventName = readJsonStringOrNull(parser)
      "session_id", "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "cwd", "project_path", "projectPath", "workspace_path", "workspacePath" -> cwd = readJsonStringOrNull(parser)
      "tool_name", "toolName" -> toolName = readJsonStringOrNull(parser)
      "tool_input", "toolInput" -> toolInputProjectFilePaths += readToolInputProjectFilePaths(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ClaudeHookPayload(
    hookEventName = hookEventName,
    sessionId = sessionId,
    cwd = cwd,
    toolName = toolName,
    toolInputProjectFilePaths = toolInputProjectFilePaths,
  )
}

private fun readToolInputProjectFilePaths(parser: JsonParser): Set<String> {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return emptySet()
  }

  val paths = LinkedHashSet<String>()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "file_path", "filePath", "notebook_path", "notebookPath" -> readJsonStringOrNull(parser)?.let(paths::add)
      else -> parser.skipChildren()
    }
    true
  }
  return paths
}

private fun createHookUpdate(payload: ClaudeHookPayload, sessionId: String): ClaudeHookUpdateResolution {
  val hookEventName = payload.hookEventName?.normalizeHookEventName()
                      ?: return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.MISSING_HOOK_EVENT)
  return when (hookEventName) {
    "pretooluse" -> {
      val toolName = payload.toolName?.trim()?.takeIf { it.isNotEmpty() }
                     ?: return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.MISSING_TOOL_NAME)
      if (!isClaudeUserInteractionToolName(toolName)) {
        return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.UNSUPPORTED_PRE_TOOL)
      }
      val scopedPath = payload.cwd?.let(::normalizeHookProjectPath)
                       ?: return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.MISSING_OR_MALFORMED_CWD)
      ClaudeHookUpdateResolution.Emitted(
        AgentSessionSourceUpdateEvent(
          type = AgentSessionSourceUpdate.HINTS_CHANGED,
          scopedPaths = setOf(scopedPath),
          activityUpdatesByThreadId = mapOf(
            sessionId to AgentSessionThreadActivityUpdate(
              activityReport = AgentThreadActivityReport(AgentThreadActivity.NEEDS_INPUT),
            )
          ),
        )
      )
    }
    "posttooluse" -> {
      val toolName = payload.toolName?.trim()?.takeIf { it.isNotEmpty() }
                     ?: return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.MISSING_TOOL_NAME)
      if (!isClaudeHookProjectMutatingToolName(toolName)) {
        return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.UNSUPPORTED_POST_TOOL)
      }
      val scopedPath = payload.cwd?.let(::normalizeHookProjectPath)
                       ?: return ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.MISSING_OR_MALFORMED_CWD)
      ClaudeHookUpdateResolution.Emitted(
        AgentSessionSourceUpdateEvent(
          type = AgentSessionSourceUpdate.HINTS_CHANGED,
          scopedPaths = setOf(scopedPath),
          threadIds = setOf(sessionId),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = resolveChangedProjectFilePaths(
            paths = payload.toolInputProjectFilePaths,
            projectPath = scopedPath,
          ),
        )
      )
    }
    else -> ClaudeHookUpdateResolution.Ignored(ClaudeHookUpdateIgnoredReason.UNSUPPORTED_HOOK_EVENT)
  }
}

private sealed interface ClaudeHookUpdateResolution {
  data class Emitted(@JvmField val updateEvent: AgentSessionSourceUpdateEvent) : ClaudeHookUpdateResolution
  data class Ignored(@JvmField val reason: ClaudeHookUpdateIgnoredReason) : ClaudeHookUpdateResolution
}

private enum class ClaudeHookUpdateIgnoredReason {
  MISSING_HOOK_EVENT,
  MISSING_TOOL_NAME,
  MISSING_OR_MALFORMED_CWD,
  UNSUPPORTED_HOOK_EVENT,
  UNSUPPORTED_PRE_TOOL,
  UNSUPPORTED_POST_TOOL,
}

private fun normalizeHookProjectPath(path: String): String? {
  val parsedPath = parseAgentWorkbenchPathOrNull(path)?.normalize()?.takeIf { it.isAbsolute } ?: return null
  return normalizeAgentWorkbenchPath(parsedPath.toString()).takeIf { it.isNotBlank() }
}

private fun resolveChangedProjectFilePaths(paths: Set<String>, projectPath: String): Set<String>? {
  val changedProjectFilePaths = LinkedHashSet<String>()
  for (path in paths) {
    val resolvedPath = resolveChangedProjectFilePath(path = path, projectPath = projectPath) ?: return null
    changedProjectFilePaths.add(resolvedPath)
  }
  return changedProjectFilePaths.takeIf { it.isNotEmpty() }
}

private fun resolveChangedProjectFilePath(path: String, projectPath: String): String? {
  val parsedPath = parseAgentWorkbenchPathOrNull(path)?.normalize() ?: return null
  val resolvedPath = if (parsedPath.isAbsolute) {
    parsedPath
  }
  else {
    parseAgentWorkbenchPathOrNull(projectPath)?.takeIf { it.isAbsolute }?.resolve(parsedPath)?.normalize() ?: return null
  }
  return normalizeAgentWorkbenchPath(resolvedPath.toString())
}

private fun String.normalizeHookEventName(): String = trim().lowercase().replace("_", "").replace("-", "")

private fun String.toFileNameToken(): String {
  return filter(Char::isLetterOrDigit).take(24).ifEmpty { "token" }
}

private const val CLAUDE_HOOK_HTTP_TIMEOUT_SECONDS: Int = 1
