// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.math.max

internal fun normalizeContextItemsForProject(
  items: List<AgentPromptContextItem>,
  projectPath: String?,
): List<AgentPromptContextItem> {
  if (items.isEmpty()) {
    return emptyList()
  }
  return items.mapNotNull { item -> normalizeContextItemForProject(item, projectPath) }
}

internal fun materializeVisibleContextEntries(
  autoEntries: List<ContextEntry>,
  manualItemsBySourceId: Map<String, AgentPromptContextItem>,
  projectPath: String?,
): List<ContextEntry> {
  if (autoEntries.isEmpty() && manualItemsBySourceId.isEmpty()) {
    return emptyList()
  }

  val visibleAutoEntries = autoEntries.mapNotNull { entry ->
    val normalizedItem = normalizeContextItemForProject(entry.item, projectPath) ?: return@mapNotNull null
    entry.copy(
      item = normalizedItem,
      projectBasePath = projectPath,
    )
  }
  val visibleManualEntries = manualItemsBySourceId.entries.mapNotNull { (sourceId, item) ->
    val normalizedItem = normalizeContextItemForProject(item, projectPath) ?: return@mapNotNull null
    ContextEntry(
      item = normalizedItem,
      projectBasePath = projectPath,
      id = "manual:$sourceId",
      origin = ContextEntryOrigin.MANUAL,
      manualSourceId = sourceId,
    )
  }
  return visibleAutoEntries + visibleManualEntries
}

internal fun normalizeContextItemForProject(
  item: AgentPromptContextItem,
  projectPath: String?,
): AgentPromptContextItem? {
  return when (item.rendererId) {
    AgentPromptContextRendererIds.FILE -> normalizeFileContextItem(item, projectPath)
    AgentPromptContextRendererIds.PATHS -> normalizePathsContextItem(item, projectPath)
    else -> item
  }
}

private fun normalizeFileContextItem(item: AgentPromptContextItem, projectPath: String?): AgentPromptContextItem? {
  val payload = item.payload.objOrNull()
  val pathText = payload?.string("path") ?: item.body
  return if (isImplicitCurrentProjectRoot(pathText, projectPath)) null else item
}

private fun normalizePathsContextItem(item: AgentPromptContextItem, projectPath: String?): AgentPromptContextItem? {
  val pathEntries = extractPathEntries(item)
  if (pathEntries.isEmpty()) {
    return item
  }

  val filteredEntries = pathEntries.filterNot { entry -> isImplicitCurrentProjectRoot(entry.path, projectPath) }
  if (filteredEntries.size == pathEntries.size) {
    return item
  }
  if (filteredEntries.isEmpty()) {
    return null
  }

  val updatedBody = filteredEntries.joinToString(separator = "\n", transform = ::renderPathEntry)
  val updatedPayload = updatePathsPayload(
    payload = item.payload,
    filteredEntries = filteredEntries,
    removedCount = pathEntries.size - filteredEntries.size,
  )
  val updatedTruncation = updateTruncation(item.truncation, oldBody = item.body, newBody = updatedBody)
  return item.copy(
    body = updatedBody,
    payload = updatedPayload,
    truncation = updatedTruncation,
  )
}

private fun extractPathEntries(item: AgentPromptContextItem): List<PathContextEntry> {
  val payloadEntries = item.payload.objOrNull()
    ?.array("entries")
    ?.mapNotNull { value ->
      val entry = value.objOrNull() ?: return@mapNotNull null
      val path = entry.string("path")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      PathContextEntry(
        path = path,
        kind = normalizePathKind(entry.string("kind")),
      )
    }
    .orEmpty()
  if (payloadEntries.isNotEmpty()) {
    return payloadEntries
  }

  return item.body
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map { line ->
      when {
        line.startsWith("file:", ignoreCase = true) -> PathContextEntry(
          path = line.substringAfter(':').trim(),
          kind = "file",
        )
        line.startsWith("dir:", ignoreCase = true) -> PathContextEntry(
          path = line.substringAfter(':').trim(),
          kind = "dir",
        )
        else -> PathContextEntry(path = line, kind = null)
      }
    }
    .filter { entry -> entry.path.isNotEmpty() }
    .toList()
}

private fun updatePathsPayload(
  payload: AgentPromptPayloadValue,
  filteredEntries: List<PathContextEntry>,
  removedCount: Int,
): AgentPromptPayloadValue {
  val payloadObject = payload.objOrNull() ?: return payload
  if (payloadObject.array("entries") == null) {
    return payload
  }

  val updatedFields = LinkedHashMap(payloadObject.fields)
  updatedFields["entries"] = AgentPromptPayloadValue.Arr(filteredEntries.map(::toPayloadEntry))

  payloadObject.number("selectedCount")
    ?.toIntOrNull()
    ?.let { count -> updatedFields["selectedCount"] = AgentPromptPayload.num(max(filteredEntries.size, count - removedCount)) }
  payloadObject.number("includedCount")
    ?.let { updatedFields["includedCount"] = AgentPromptPayload.num(filteredEntries.size) }
  if (payloadObject.fields.containsKey("directoryCount")) {
    updatedFields["directoryCount"] = AgentPromptPayload.num(filteredEntries.count { entry -> entry.kind == "dir" })
  }
  if (payloadObject.fields.containsKey("fileCount")) {
    updatedFields["fileCount"] = AgentPromptPayload.num(filteredEntries.count { entry -> entry.kind != "dir" })
  }

  return AgentPromptPayloadValue.Obj(updatedFields)
}

private fun toPayloadEntry(entry: PathContextEntry): AgentPromptPayloadValue {
  val fields = linkedMapOf<String, AgentPromptPayloadValue>()
  entry.kind?.let { kind -> fields["kind"] = AgentPromptPayload.str(kind) }
  fields["path"] = AgentPromptPayload.str(entry.path)
  return AgentPromptPayloadValue.Obj(fields)
}

private fun renderPathEntry(entry: PathContextEntry): String {
  return entry.kind?.let { kind -> "$kind: ${entry.path}" } ?: entry.path
}

private fun updateTruncation(
  truncation: AgentPromptContextTruncation,
  oldBody: String,
  newBody: String,
): AgentPromptContextTruncation {
  if (oldBody == newBody) {
    return truncation
  }
  return AgentPromptContextTruncation(
    originalChars = if (truncation.reason == AgentPromptContextTruncationReason.NONE) newBody.length else truncation.originalChars,
    includedChars = newBody.length,
    reason = truncation.reason,
  )
}

private fun isImplicitCurrentProjectRoot(pathText: String, projectPath: String?): Boolean {
  val normalizedProjectPath = normalizeAbsoluteProjectPath(projectPath) ?: return false
  val resolvedPath = resolveContextPath(pathText, normalizedProjectPath) ?: return false
  return FileUtil.pathsEqual(normalizedProjectPath, resolvedPath)
}

private fun normalizeAbsoluteProjectPath(projectPath: String?): String? {
  val normalized = projectPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return try {
    Path.of(normalized).normalize().toString()
  }
  catch (_: InvalidPathException) {
    null
  }
}

private fun resolveContextPath(pathText: String, projectPath: String): String? {
  val normalizedPath = pathText.trim()
  if (normalizedPath.isEmpty()) {
    return null
  }
  return try {
    val path = Path.of(normalizedPath)
    if (path.isAbsolute) {
      path.normalize().toString()
    }
    else {
      Path.of(projectPath)
        .resolve(path)
        .normalize()
        .toString()
    }
  }
  catch (_: InvalidPathException) {
    null
  }
}

private fun normalizePathKind(kind: String?): String? {
  return kind
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }
}

private data class PathContextEntry(
  @JvmField val path: String,
  @JvmField val kind: String?,
)
