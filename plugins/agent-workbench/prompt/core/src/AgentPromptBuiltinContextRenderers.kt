// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

// @spec plugins/air/spec/prompt-context/prompt-context-contracts.spec.md

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.math.max

private const val CHIP_PREVIEW_MAX_LENGTH = 60
private const val TREE_SELECTION_ITEM_ID = "tree.selection"

internal class AgentPromptSnippetContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.SNIPPET

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val payload = item.payload.objOrNull() ?: AgentPromptPayloadValue.Obj.EMPTY
    val startLine = payload.number("startLine")?.takeIf { it.isNotBlank() }
    val endLine = payload.number("endLine")?.takeIf { it.isNotBlank() }
    val selection = payload.bool("selection")
    val language = normalizeLanguage(payload.string("language"))

    val details = mutableListOf<String>()
    if (startLine != null && endLine != null) {
      details += "lines=$startLine-$endLine"
    }
    selection?.let { details += "selection=$it" }

    val descriptor = buildString {
      append("snippet")
      if (details.isNotEmpty()) {
        append(": ")
        append(details.joinToString(separator = " "))
      }
      append(renderTruncationSuffix(item))
    }
    return descriptor + "\n" + appendCodeBlock(language = language, content = item.body)
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    return AgentPromptChipRender(text = compactSnippetChipTitle(input.item) ?: input.item.title ?: "Snippet")
  }

  private fun compactSnippetChipTitle(item: AgentPromptContextItem): String? {
    if (item.itemId == AgentPromptContextItemIds.CHANGES_SELECTION) {
      return "Changes"
    }

    val payload = item.payload.objOrNull()
    if (item.itemId == TREE_SELECTION_ITEM_ID) {
      payload?.string("treeKind")?.trim()?.takeIf { it.isNotEmpty() }?.let { treeKind ->
        return "Selection ($treeKind)"
      }
    }

    if (payload?.bool("selection") == false) {
      val startLine = payload.number("startLine")?.takeIf { it.isNotBlank() }
      val endLine = payload.number("endLine")?.takeIf { it.isNotBlank() }
      if (startLine != null && endLine != null) {
        return "Caret ($startLine-$endLine)"
      }
    }

    val title = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
      title.startsWith("Caret Context ") -> "Caret " + title.removePrefix("Caret Context ")
      title.startsWith("Tree Selection ") -> "Selection " + title.removePrefix("Tree Selection ")
      else -> null
    }
  }
}

class AgentPromptFileContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.FILE

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val payload = item.payload.objOrNull()
    val pathText = payload?.string("path") ?: item.body
    val absolutePath = absolutizePath(pathText = pathText, projectPath = input.projectPath)
    return "file: $absolutePath${renderTruncationSuffix(item)}"
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    val payload = input.item.payload.objOrNull()
    val pathText = payload?.string("path") ?: input.item.body
    val shortened = shortenPathForChip(pathText, input.projectBasePath)
    val text = if (shortened.isBlank()) composePathChipText(input.item.title, shortened) else composePathChipText(title = null, preview = shortened)
    return AgentPromptChipRender(text = text)
  }
}

class AgentPromptSymbolContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.SYMBOL

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    return "symbol: ${item.body}${renderTruncationSuffix(item)}"
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    return AgentPromptChipRender(text = composePreviewChipText(input.item.title, input.item.body))
  }
}

class AgentPromptPathsContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.PATHS

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val paths = extractPaths(item)
      .map { absolutizePath(pathText = it, projectPath = input.projectPath) }
    val truncation = renderTruncationSuffix(item)
    return if (paths.size == 1) {
      "path: ${paths[0]}$truncation"
    }
    else {
      buildString {
        append("paths:")
        append(truncation)
        if (paths.isNotEmpty()) {
          append('\n')
          append(paths.joinToString(separator = "\n"))
        }
      }
    }
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    val first = extractPaths(input.item).firstOrNull().orEmpty()
    val preview = shortenPathForChip(first, input.projectBasePath)
    val text = if (preview.isBlank()) composePathChipText(input.item.title, preview) else composePathChipText(title = null, preview = preview)
    return AgentPromptChipRender(text = text)
  }

  private fun extractPaths(item: AgentPromptContextItem): List<String> {
    val payload = item.payload.objOrNull()
    val entries = payload?.array("entries")
      ?.mapNotNull { value ->
        val entry = value.objOrNull() ?: return@mapNotNull null
        entry.string("path")?.trim()?.takeIf { it.isNotEmpty() }
      }
      .orEmpty()
    if (entries.isNotEmpty()) {
      return entries
    }
    // Body text may have "dir: " / "file: " prefix — strip it
    return item.body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { line ->
        when {
          line.startsWith("file:", ignoreCase = true) -> line.substringAfter(':').trim()
          line.startsWith("dir:", ignoreCase = true) -> line.substringAfter(':').trim()
          else -> line
        }
      }
      .toList()
  }
}

internal fun renderTruncationSuffix(item: AgentPromptContextItem): String {
  val truncation = item.truncation
  if (truncation.reason == AgentPromptContextTruncationReason.NONE) {
    return ""
  }
  return " [truncated=${truncation.reason.name.lowercase()} ${truncation.includedChars}/${truncation.originalChars}]"
}

internal fun appendCodeBlock(language: String?, content: String): String {
  val fence = "`".repeat(max(3, maxConsecutiveBackticks(content) + 1))
  return buildString(content.length + 16) {
    append(fence)
    language?.let { append(it) }
    append('\n')
    append(content)
    append('\n')
    append(fence)
  }
}

internal fun maxConsecutiveBackticks(value: String): Int {
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

internal fun normalizeLanguage(raw: String?): String? {
  val normalized = raw
    ?.trim()
    ?.lowercase()
    ?.replace(' ', '-')
    .orEmpty()
  if (normalized.isEmpty()) {
    return null
  }
  return if (normalized.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '+' || ch == '.' || ch == '#' }) {
    normalized
  }
  else {
    null
  }
}

internal fun absolutizePath(pathText: String, projectPath: String?): String {
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

internal fun composePreviewChipText(title: String?, preview: String): String {
  val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Context"
  val trimmedPreview = preview.trim()
  if (trimmedPreview.isEmpty()) {
    return resolvedTitle
  }
  return if (trimmedPreview.length <= CHIP_PREVIEW_MAX_LENGTH) trimmedPreview else trimmedPreview.take(CHIP_PREVIEW_MAX_LENGTH) + "\u2026"
}

internal fun composePathChipText(title: String?, preview: String): String {
  val resolvedTitle = title?.takeIf { it.isNotBlank() }
  val trimmedPreview = preview.trim()
  if (trimmedPreview.isEmpty()) {
    return resolvedTitle ?: "Context"
  }
  val shortPreview = StringUtil.shortenPathWithEllipsis(trimmedPreview, CHIP_PREVIEW_MAX_LENGTH, true)
  return if (resolvedTitle == null) shortPreview else "$resolvedTitle: $shortPreview"
}

internal fun shortenPathForChip(value: String, projectBasePath: String?): String {
  if (!OSAgnosticPathUtil.isAbsolute(value)) {
    return value
  }

  val path = FileUtilRt.toSystemDependentName(value)
  val projectPath = projectBasePath
    ?.takeIf { it.isNotBlank() }
    ?.let(FileUtilRt::toSystemDependentName)
  if (!projectPath.isNullOrBlank() && FileUtil.isAncestor(projectPath, path, false)) {
    val relative = FileUtilRt.getRelativePath(projectPath, path, FileSystems.getDefault().separator[0])
    return if (relative.isNullOrEmpty()) "." else relative
  }

  val userHome = FileUtilRt.toSystemDependentName(SystemProperties.getUserHome())
  if (FileUtil.isAncestor(userHome, path, false)) {
    return if (FileUtil.pathsEqual(userHome, path)) {
      "~"
    }
    else {
      FileUtil.getLocationRelativeToUserHome(path, false)
    }
  }

  return path
}
