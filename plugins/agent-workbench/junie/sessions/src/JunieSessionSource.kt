// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<JunieSessionSource>()

internal class JunieSessionSource(
  private val sessionIndexPathProvider: () -> Path = ::defaultJunieSessionIndexPath,
  private val jsonFactory: JsonFactory = JsonFactory(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.JUNIE) {
  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    val matchingEntries = loadIndexEntries(sessionIndexPathProvider())
      .filter { it.normalizedProjectDir == normalizedProjectPath }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
    rememberActiveThreadRead(matchingEntries, JunieSessionIndexEntry::sessionId, JunieSessionIndexEntry::updatedAt)
    return matchingEntries.map { it.toAgentSessionThread(readTracker) }
  }

  private fun loadIndexEntries(indexPath: Path): List<JunieSessionIndexEntry> {
    if (!Files.isRegularFile(indexPath)) {
      return emptyList()
    }

    return try {
      val entries = WorkbenchJsonlScanner.scanJsonObjects(
        path = indexPath,
        jsonFactory = jsonFactory,
        newState = { ArrayList<JunieSessionIndexEntry>() },
      ) { parser, state ->
        parseIndexEntry(parser)?.let(state::add)
        true
      }
      entries.latestBySessionId()
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Junie session index: $indexPath", e)
      emptyList()
    }
  }
}

private data class JunieSessionIndexEntry(
  val sessionId: String,
  val title: String,
  val updatedAt: Long,
  val normalizedProjectDir: String,
)

private fun defaultJunieSessionIndexPath(): Path {
  return Path.of(System.getProperty("user.home") ?: ".", ".junie", "sessions", "index.jsonl")
}

private fun parseIndexEntry(parser: JsonParser): JunieSessionIndexEntry? {
  var sessionId: String? = null
  var createdAt: Long? = null
  var updatedAt: Long? = null
  var projectDir: String? = null
  var taskName: String? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "createdAt" -> createdAt = readJsonLongOrNull(parser)
      "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      "projectDir" -> projectDir = readJsonStringOrNull(parser)
      "taskName" -> taskName = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedProjectDir = projectDir?.let(::normalizeJunieProjectPath) ?: return null
  val resolvedUpdatedAt = updatedAt ?: createdAt ?: 0L
  val resolvedTitle = taskName?.normalizeJunieSessionTitle() ?: normalizedSessionId
  return JunieSessionIndexEntry(
    sessionId = normalizedSessionId,
    title = resolvedTitle,
    updatedAt = resolvedUpdatedAt,
    normalizedProjectDir = normalizedProjectDir,
  )
}

private fun List<JunieSessionIndexEntry>.latestBySessionId(): List<JunieSessionIndexEntry> {
  val latestBySessionId = LinkedHashMap<String, JunieSessionIndexEntry>()
  for (entry in this) {
    val previous = latestBySessionId[entry.sessionId]
    if (previous == null || entry.updatedAt >= previous.updatedAt) {
      latestBySessionId[entry.sessionId] = entry
    }
  }
  return latestBySessionId.values.toList()
}

private fun JunieSessionIndexEntry.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    activity = effectiveActivity(readTracker),
    provider = AgentSessionProvider.JUNIE,
  )
}

private fun JunieSessionIndexEntry.effectiveActivity(readTracker: Map<String, Long>): AgentThreadActivity {
  val lastSeenAt = readTracker[sessionId] ?: return AgentThreadActivity.READY
  if (updatedAt > lastSeenAt) return AgentThreadActivity.UNREAD
  return AgentThreadActivity.READY
}

private fun normalizeJunieProjectPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPathOrNull(trimmedPath) ?: return null
  return normalizedPath.trimEnd('/').ifEmpty { "/" }
}

private fun String.normalizeJunieSessionTitle(): String? {
  return replace('\n', ' ')
    .replace('\r', ' ')
    .replace(THREAD_TITLE_WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
}

private val THREAD_TITLE_WHITESPACE = Regex("\\s+")
