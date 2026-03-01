// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.math.max

private const val CONTEXT_HEADER = "### IDE Context"
private const val SOFT_CAP_OMITTED_STUB = "[omitted due to soft cap]"
private const val SOFT_CAP_PARTIAL_SUFFIX = "\n...[truncated:soft_cap_partial]"
private const val MIN_PARTIAL_BODY_CHARS = 96

private val RESERVED_METADATA_KEYS = setOf(
  AgentPromptContextMetadataKeys.SOURCE,
  AgentPromptContextMetadataKeys.PHASE,
  AgentPromptContextMetadataKeys.ORIGINAL_CHARS,
  AgentPromptContextMetadataKeys.INCLUDED_CHARS,
  AgentPromptContextMetadataKeys.TRUNCATED,
  AgentPromptContextMetadataKeys.TRUNCATION_REASON,
)

object AgentPromptContextEnvelopeFormatter {
  const val DEFAULT_SOFT_CAP_CHARS: Int = 12_000

  data class SoftCapTrimResult(
    @JvmField val items: List<AgentPromptContextItem>,
    @JvmField val serializedChars: Int,
    @JvmField val exceedsSoftCap: Boolean,
  )

  fun composeInitialMessage(request: AgentPromptInitialMessageRequest): String {
    val prompt = request.prompt.trim()
    if (request.contextItems.isEmpty()) {
      return prompt
    }

    val summary = request.contextEnvelopeSummary ?: AgentPromptContextEnvelopeSummary(
      softCapChars = DEFAULT_SOFT_CAP_CHARS,
      softCapExceeded = false,
      autoTrimApplied = false,
    )
    val contextBlock = renderContextBlock(
      items = request.contextItems,
      summary = summary,
      projectPath = request.projectPath,
    )
    if (prompt.isEmpty()) {
      return contextBlock
    }
    return buildString(prompt.length + contextBlock.length + 2) {
      append(prompt)
      append("\n\n")
      append(contextBlock)
    }
  }

  fun measureContextBlockChars(
    items: List<AgentPromptContextItem>,
    summary: AgentPromptContextEnvelopeSummary = AgentPromptContextEnvelopeSummary(softCapChars = DEFAULT_SOFT_CAP_CHARS),
    projectPath: String? = null,
  ): Int {
    return renderContextBlock(items = items, summary = summary, projectPath = projectPath).length
  }

  fun renderContextBlock(
    items: List<AgentPromptContextItem>,
    summary: AgentPromptContextEnvelopeSummary,
    projectPath: String? = null,
  ): String {
    val normalizedItems = items.map { item -> item.toEnvelopeItem() }
    return buildContextBlock(items = normalizedItems, summary = summary, projectPath = projectPath)
  }

  fun applySoftCap(
    items: List<AgentPromptContextItem>,
    softCapChars: Int = DEFAULT_SOFT_CAP_CHARS,
    projectPath: String? = null,
  ): SoftCapTrimResult {
    if (items.isEmpty()) {
      return SoftCapTrimResult(items = emptyList(), serializedChars = 0, exceedsSoftCap = false)
    }

    val summary = AgentPromptContextEnvelopeSummary(
      softCapChars = softCapChars,
      softCapExceeded = true,
      autoTrimApplied = true,
    )
    val working = items.map { normalizeItemMetadata(it) }.toMutableList()

    var serializedChars = measureContextBlockChars(items = working, summary = summary, projectPath = projectPath)
    while (serializedChars > softCapChars) {
      val updated = shrinkOneItem(working, serializedChars - softCapChars)
      if (!updated) {
        break
      }
      serializedChars = measureContextBlockChars(items = working, summary = summary, projectPath = projectPath)
    }

    return SoftCapTrimResult(
      items = working,
      serializedChars = serializedChars,
      exceedsSoftCap = serializedChars > softCapChars,
    )
  }

  private fun shrinkOneItem(items: MutableList<AgentPromptContextItem>, excessChars: Int): Boolean {
    for (index in items.indices.reversed()) {
      val current = items[index]
      val content = current.content.trim()
      if (content.isEmpty() || content == SOFT_CAP_OMITTED_STUB) {
        continue
      }

      val partiallyTrimmed = shrinkForSoftCap(content, excessChars)
      if (partiallyTrimmed != null) {
        items[index] = updateTruncationMetadata(
          item = current,
          content = partiallyTrimmed,
          reason = AgentPromptContextTruncationReasons.SOFT_CAP_PARTIAL,
        )
        return true
      }

      items[index] = updateTruncationMetadata(
        item = current,
        content = SOFT_CAP_OMITTED_STUB,
        reason = AgentPromptContextTruncationReasons.SOFT_CAP_OMITTED,
      )
      return true
    }

    return false
  }

  private fun shrinkForSoftCap(content: String, excessChars: Int): String? {
    val body = content.trim()
    if (body.length <= MIN_PARTIAL_BODY_CHARS) {
      return null
    }

    val dynamicCut = max(excessChars, body.length / 3)
    val targetBodyLength = (body.length - dynamicCut)
      .coerceAtLeast(MIN_PARTIAL_BODY_CHARS)
    if (targetBodyLength + SOFT_CAP_PARTIAL_SUFFIX.length >= body.length) {
      return null
    }

    val nextBody = body.take(targetBodyLength).trimEnd()
    if (nextBody.length <= MIN_PARTIAL_BODY_CHARS) {
      return null
    }
    return nextBody + SOFT_CAP_PARTIAL_SUFFIX
  }

  private fun normalizeItemMetadata(item: AgentPromptContextItem): AgentPromptContextItem {
    val content = item.content.trim()
    val metadata = LinkedHashMap(item.metadata)

    val originalChars = metadata[AgentPromptContextMetadataKeys.ORIGINAL_CHARS]
      ?.toIntOrNull()
      ?.coerceAtLeast(content.length)
      ?: content.length
    val includedChars = metadata[AgentPromptContextMetadataKeys.INCLUDED_CHARS]
      ?.toIntOrNull()
      ?.coerceAtLeast(0)
      ?: content.length
    val reason = metadata[AgentPromptContextMetadataKeys.TRUNCATION_REASON]
      ?: if (includedChars < originalChars) AgentPromptContextTruncationReasons.SOURCE_LIMIT else AgentPromptContextTruncationReasons.NONE
    val truncated = metadata[AgentPromptContextMetadataKeys.TRUNCATED]
      ?.toBooleanStrictOrNull()
      ?: (reason != AgentPromptContextTruncationReasons.NONE || includedChars < originalChars)

    metadata[AgentPromptContextMetadataKeys.ORIGINAL_CHARS] = originalChars.toString()
    metadata[AgentPromptContextMetadataKeys.INCLUDED_CHARS] = includedChars.toString()
    metadata[AgentPromptContextMetadataKeys.TRUNCATED] = truncated.toString()
    metadata[AgentPromptContextMetadataKeys.TRUNCATION_REASON] = if (truncated) reason else AgentPromptContextTruncationReasons.NONE

    return item.copy(content = content, metadata = metadata)
  }

  private fun updateTruncationMetadata(
    item: AgentPromptContextItem,
    content: String,
    reason: String,
  ): AgentPromptContextItem {
    val normalizedContent = content.trim()
    val metadata = LinkedHashMap(item.metadata)
    val originalChars = metadata[AgentPromptContextMetadataKeys.ORIGINAL_CHARS]
      ?.toIntOrNull()
      ?.coerceAtLeast(normalizedContent.length)
      ?: max(normalizedContent.length, item.content.length)

    metadata[AgentPromptContextMetadataKeys.ORIGINAL_CHARS] = originalChars.toString()
    metadata[AgentPromptContextMetadataKeys.INCLUDED_CHARS] = normalizedContent.length.toString()
    metadata[AgentPromptContextMetadataKeys.TRUNCATED] = true.toString()
    metadata[AgentPromptContextMetadataKeys.TRUNCATION_REASON] = reason

    return item.copy(content = normalizedContent, metadata = metadata)
  }

  private fun buildContextBlock(
    items: List<EnvelopeItem>,
    summary: AgentPromptContextEnvelopeSummary,
    projectPath: String?,
  ): String {
    val normalizedSummary = summary.normalized()
    val builder = StringBuilder(items.sumOf { it.content.length } + 128)
    builder.append(CONTEXT_HEADER)
    if (normalizedSummary.softCapExceeded) {
      builder.append('\n')
        .append("soft-cap: limit=")
        .append(normalizedSummary.softCapChars)
        .append(" auto-trim=")
        .append(booleanAsYesNo(normalizedSummary.autoTrimApplied))
    }
    if (items.isNotEmpty()) {
      builder.append("\n\n")
    }

    items.forEachIndexed { index, item ->
      if (index > 0) {
        builder.append('\n')
      }
      renderItem(builder = builder, item = item, projectPath = projectPath)
    }

    return builder.toString().trimEnd()
  }

  private fun renderItem(builder: StringBuilder, item: EnvelopeItem, projectPath: String?) {
    when (item.kind) {
      "snippet" -> {
        val language = snippetLanguage(item)
        builder.append(renderSnippetDescriptor(item, language)).append('\n')
        appendCodeBlock(builder = builder, language = language, content = item.content)
      }

      "file" -> {
        builder.append("file: ")
          .append(absolutizePath(pathText = item.content, projectPath = projectPath))
          .append(renderTruncationSuffix(item))
          .append('\n')
      }

      "symbol" -> {
        builder.append("symbol: ")
          .append(item.content)
          .append(renderTruncationSuffix(item))
          .append('\n')
      }

      "paths" -> {
        builder.append("paths:")
          .append(renderTruncationSuffix(item))
          .append('\n')
        renderPaths(builder = builder, content = item.content, projectPath = projectPath)
      }

      else -> {
        builder.append("context: kind=")
          .append(item.kind)
          .append(" title=")
          .append(item.title)
          .append(renderTruncationSuffix(item))
          .append('\n')
        appendCodeBlock(builder = builder, language = "text", content = item.content)
      }
    }
  }

  private fun renderSnippetDescriptor(item: EnvelopeItem, language: String?): String {
    val details = mutableListOf<String>()
    val startLine = item.metadata["startLine"]?.takeIf { it.isNotBlank() }
    val endLine = item.metadata["endLine"]?.takeIf { it.isNotBlank() }
    if (startLine != null && endLine != null) {
      details += "lines=$startLine-$endLine"
    }
    item.metadata["selection"]
      ?.takeIf { it.isNotBlank() }
      ?.let { selection -> details += "selection=$selection" }
    language?.let { details += "lang=$it" }

    return buildString {
      append("snippet")
      if (details.isNotEmpty()) {
        append(": ")
        append(details.joinToString(separator = " "))
      }
      append(renderTruncationSuffix(item))
    }
  }

  private fun snippetLanguage(item: EnvelopeItem): String? {
    val raw = item.metadata[AgentPromptContextMetadataKeys.LANGUAGE]
      ?.trim()
      ?.lowercase()
      ?.replace(' ', '-')
      .orEmpty()
    if (raw.isEmpty()) {
      return null
    }
    return if (raw.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '+' || ch == '.' || ch == '#' }) {
      raw
    }
    else {
      null
    }
  }

  private fun renderPaths(builder: StringBuilder, content: String, projectPath: String?) {
    val lines = content.lineSequence()
      .map { line -> line.trim() }
      .filter { line -> line.isNotEmpty() }
      .toList()
    lines.forEach { line ->
      builder.append(renderPathLine(line = line, projectPath = projectPath)).append('\n')
    }
  }

  private fun renderPathLine(line: String, projectPath: String?): String {
    val parsed = parsePathLine(line)
    val absolutePath = absolutizePath(pathText = parsed.pathText, projectPath = projectPath)
    return when (parsed.prefix) {
      "file" -> "file: $absolutePath"
      "dir" -> "dir: $absolutePath"
      else -> absolutePath
    }
  }

  private fun parsePathLine(line: String): ParsedPathLine {
    return when {
      line.startsWith("file:", ignoreCase = true) -> ParsedPathLine(prefix = "file", pathText = line.substringAfter(':').trim())
      line.startsWith("dir:", ignoreCase = true) -> ParsedPathLine(prefix = "dir", pathText = line.substringAfter(':').trim())
      else -> ParsedPathLine(prefix = null, pathText = line)
    }
  }

  private fun absolutizePath(pathText: String, projectPath: String?): String {
    val normalizedPath = pathText.trim()
    if (normalizedPath.isEmpty()) {
      return "[path-unresolved]"
    }
    val resolved = resolveAbsolutePath(pathText = normalizedPath, projectPath = projectPath)
    return resolved ?: "$normalizedPath [path-unresolved]"
  }

  private fun resolveAbsolutePath(pathText: String, projectPath: String?): String? {
    return try {
      val path = Path.of(pathText)
      if (path.isAbsolute) {
        path.normalize().toString()
      }
      else {
        val normalizedProjectPath = projectPath
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?: return null
        Path.of(normalizedProjectPath)
          .resolve(path)
          .normalize()
          .toString()
      }
    }
    catch (_: InvalidPathException) {
      null
    }
  }

  private fun renderTruncationSuffix(item: EnvelopeItem): String {
    if (!item.truncated) {
      return ""
    }
    return " [truncated=${item.truncationReason} ${item.includedChars}/${item.originalChars}]"
  }

  private fun appendCodeBlock(builder: StringBuilder, language: String?, content: String) {
    val fence = "`".repeat(max(3, maxConsecutiveBackticks(content) + 1))
    builder.append(fence)
    language?.let { builder.append(it) }
    builder
      .append('\n')
      .append(content)
      .append('\n')
      .append(fence)
      .append('\n')
  }

  private fun maxConsecutiveBackticks(value: String): Int {
    var best = 0
    var current = 0
    value.forEach { ch ->
      if (ch == '`') {
        current += 1
        if (current > best) {
          best = current
        }
      }
      else {
        current = 0
      }
    }
    return best
  }

  private fun booleanAsYesNo(value: Boolean): String {
    return if (value) "yes" else "no"
  }

  private fun AgentPromptContextEnvelopeSummary.normalized(): AgentPromptContextEnvelopeSummary {
    val normalizedSoftCap = softCapChars.coerceAtLeast(1)
    return if (normalizedSoftCap == softCapChars) {
      this
    }
    else {
      copy(softCapChars = normalizedSoftCap)
    }
  }

  private fun AgentPromptContextItem.toEnvelopeItem(): EnvelopeItem {
    val content = this.content.trim()
    val originalChars = metadata[AgentPromptContextMetadataKeys.ORIGINAL_CHARS]
      ?.toIntOrNull()
      ?.coerceAtLeast(content.length)
      ?: content.length
    val includedChars = metadata[AgentPromptContextMetadataKeys.INCLUDED_CHARS]
      ?.toIntOrNull()
      ?.coerceAtLeast(0)
      ?: content.length
    val reason = metadata[AgentPromptContextMetadataKeys.TRUNCATION_REASON]
      ?: if (includedChars < originalChars) AgentPromptContextTruncationReasons.SOURCE_LIMIT else AgentPromptContextTruncationReasons.NONE
    val truncated = metadata[AgentPromptContextMetadataKeys.TRUNCATED]
      ?.toBooleanStrictOrNull()
      ?: (reason != AgentPromptContextTruncationReasons.NONE || includedChars < originalChars)

    val nonReservedMetadata = metadata
      .filterKeys { key -> key !in RESERVED_METADATA_KEYS }
      .toSortedMap()

    return EnvelopeItem(
      kind = kindId,
      title = title,
      originalChars = originalChars,
      includedChars = includedChars,
      truncated = truncated,
      truncationReason = if (truncated) reason else AgentPromptContextTruncationReasons.NONE,
      metadata = nonReservedMetadata,
      content = content,
    )
  }

  private data class ParsedPathLine(
    @JvmField val prefix: String?,
    @JvmField val pathText: String,
  )

  private data class EnvelopeItem(
    @JvmField val kind: String,
    @JvmField val title: String,
    @JvmField val originalChars: Int,
    @JvmField val includedChars: Int,
    @JvmField val truncated: Boolean,
    @JvmField val truncationReason: String,
    @JvmField val metadata: Map<String, String>,
    @JvmField val content: String,
  )
}
