// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.render

import com.intellij.agent.workbench.prompt.core.AgentPromptChipRender
import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.testrunner.composeTestsGroupLabel
import com.intellij.agent.workbench.prompt.testrunner.computeTestStatusCounts
import com.intellij.agent.workbench.prompt.testrunner.formatTestReference
import com.intellij.agent.workbench.prompt.testrunner.normalizeTestStatus
import com.intellij.agent.workbench.prompt.testrunner.normalizeTestStatusCounts

private const val STATUS_SEPARATOR = ": "
private const val ASSERTION_SEPARATOR = " | assertion: "

private data class TestEntry(
  @JvmField val name: String,
  @JvmField val locationUrl: String?,
  @JvmField val reference: String,
  @JvmField val status: String,
  @JvmField val assertionMessage: String?,
)

class AgentPromptTestFailuresContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.TEST_FAILURES

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val entries = extractEntries(item)
    val statusCounts = extractStatusCounts(item, entries)
    val label = composeTestsGroupLabel(statusCounts)
    val includeStatusPrefix = shouldIncludeStatusPrefix(statusCounts)
    val focusedOutput = extractFocusedOutput(item)
    return buildString {
      append(label)
      append(renderTruncationSuffix(item))
      if (entries.isNotEmpty()) {
        append('\n')
        append(entries.joinToString(separator = "\n") { entry -> renderEntryLine(entry, includeStatusPrefix) })
      }
      if (focusedOutput != null) {
        if (entries.isNotEmpty()) {
          append("\n\n")
        }
        else {
          append('\n')
        }
        append("focused failure output:\n")
        append(appendCodeBlock(content = focusedOutput))
      }
    }
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    val entries = extractEntries(input.item)
    val statusCounts = extractStatusCounts(input.item, entries)
    val label = composeTestsGroupLabel(statusCounts)
    val preview = resolveChipPreview(entries, input.item)
    return AgentPromptChipRender(text = composeChipText(label, preview))
  }

  private fun extractEntries(item: AgentPromptContextItem): List<TestEntry> {
    val payloadEntries = item.payload.objOrNull()
      ?.array("entries")
      ?.mapNotNull { value -> toEntry(value.objOrNull()) }
      .orEmpty()
    if (payloadEntries.isNotEmpty()) {
      return payloadEntries
    }

    return item.body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map(::parseBodyEntryLine)
      .toList()
  }

  private fun toEntry(payload: AgentPromptPayloadValue.Obj?): TestEntry? {
    payload ?: return null
    val name = payload.string("name")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val locationUrl = payload.string("locationUrl")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val reference = payload.string("reference")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val status = normalizeTestStatus(
      payload.string("status")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    )
    val assertionMessage = payload.string("assertionMessage")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    if (name == null && locationUrl == null && reference == null) {
      return null
    }
    val resolvedName = name ?: reference ?: locationUrl!!
    val resolvedReference = reference ?: formatTestReference(name = resolvedName, locationUrl = locationUrl)
    return TestEntry(
      name = resolvedName,
      locationUrl = locationUrl,
      reference = resolvedReference,
      status = status,
      assertionMessage = assertionMessage,
    )
  }

  private fun parseBodyEntryLine(line: String): TestEntry {
    val assertionSplitIndex = line.indexOf(ASSERTION_SEPARATOR)
    val assertionMessage = if (assertionSplitIndex >= 0) {
      line.substring(assertionSplitIndex + ASSERTION_SEPARATOR.length)
        .trim()
        .takeIf { it.isNotEmpty() }
    }
    else {
      null
    }
    val mainPart = if (assertionSplitIndex >= 0) {
      line.substring(0, assertionSplitIndex).trim()
    }
    else {
      line
    }

    val statusSplitIndex = mainPart.indexOf(STATUS_SEPARATOR)
    val status = if (statusSplitIndex > 0) {
      normalizeTestStatus(mainPart.substring(0, statusSplitIndex))
    }
    else {
      "unknown"
    }
    val anchor = if (statusSplitIndex > 0) {
      mainPart.substring(statusSplitIndex + STATUS_SEPARATOR.length).trim()
    }
    else {
      mainPart
    }.ifEmpty { line }
    val locationUrl = anchor.takeIf(::looksLikeLocationUrl)
    val reference = formatTestReference(name = anchor, locationUrl = locationUrl)

    return TestEntry(
      name = anchor,
      locationUrl = locationUrl,
      reference = reference,
      status = status,
      assertionMessage = assertionMessage,
    )
  }

  private fun extractStatusCounts(item: AgentPromptContextItem, entries: List<TestEntry>): LinkedHashMap<String, Int> {
    val payloadCounts = item.payload.objOrNull()
      ?.fields
      ?.get("statusCounts")
      ?.objOrNull()
      ?.fields
      ?.mapNotNull { (status, value) ->
        parseStatusCount(value)?.let { count -> status to count }
      }
      ?.toMap()
      .orEmpty()
    if (payloadCounts.isNotEmpty()) {
      return normalizeTestStatusCounts(payloadCounts)
    }
    return computeTestStatusCounts(entries.map { entry -> entry.status })
  }

  private fun extractFocusedOutput(item: AgentPromptContextItem): String? {
    return item.payload.objOrNull()
      ?.string("focusedOutput")
      ?.takeIf { it.isNotBlank() }
  }

  private fun parseStatusCount(value: AgentPromptPayloadValue): Int? {
    return when (value) {
      is AgentPromptPayloadValue.Num -> value.value.toIntOrNull()
      is AgentPromptPayloadValue.Str -> value.value.toIntOrNull()
      else -> null
    }
  }

  private fun resolveChipPreview(entries: List<TestEntry>, item: AgentPromptContextItem): String {
    val prioritizedEntry = entries.firstOrNull { entry -> entry.status == "failed" } ?: entries.firstOrNull()
    if (prioritizedEntry != null) {
      return prioritizedEntry.reference
    }

    val firstLine = item.body
      .lineSequence()
      .map { it.trim() }
      .firstOrNull { it.isNotEmpty() }
      .orEmpty()
    if (firstLine.isEmpty()) {
      return ""
    }
    return parseBodyEntryLine(firstLine).reference
  }
}

private fun renderEntryLine(entry: TestEntry, includeStatusPrefix: Boolean): String {
  val anchor = entry.reference
  val base = if (includeStatusPrefix) "${entry.status}: $anchor" else anchor
  val assertion = entry.assertionMessage ?: return base
  return "$base | assertion: $assertion"
}

private fun shouldIncludeStatusPrefix(statusCounts: Map<String, Int>): Boolean {
  val nonZeroStatuses = statusCounts
    .filterValues { count -> count > 0 }
    .keys
  if (nonZeroStatuses.size != 1) {
    return true
  }
  return when (nonZeroStatuses.single()) {
    "failed", "passed" -> false
    else -> true
  }
}

private fun looksLikeLocationUrl(value: String): Boolean {
  return value.contains("://")
}

private fun renderTruncationSuffix(item: AgentPromptContextItem): String {
  val truncation = item.truncation
  if (truncation.reason == AgentPromptContextTruncationReason.NONE) {
    return ""
  }
  return " [truncated=${truncation.reason.name.lowercase()} ${truncation.includedChars}/${truncation.originalChars}]"
}

private fun composeChipText(label: String, preview: String): String {
  val normalizedPreview = preview.trim()
  if (normalizedPreview.isEmpty()) {
    return label
  }
  val suffix = when {
    normalizedPreview.length <= 60 -> normalizedPreview
    else -> normalizedPreview.take(60) + "\u2026"
  }
  return "$label: $suffix"
}

private fun appendCodeBlock(content: String): String {
  val fence = "`".repeat(maxOf(3, maxConsecutiveBackticks(content) + 1))
  return buildString(content.length + 16) {
    append(fence)
    append("text\n")
    append(content)
    append('\n')
    append(fence)
  }
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
