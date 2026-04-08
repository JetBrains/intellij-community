// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.diagnostic.logger
import kotlin.math.max

private const val CONTEXT_HEADER = "### IDE Context"
private const val SOFT_CAP_OMITTED_STUB = "[omitted due to soft cap]"
private const val SOFT_CAP_PARTIAL_SUFFIX = "\n...[truncated:soft_cap_partial]"
private const val MIN_PARTIAL_BODY_CHARS = 96

private class AgentPromptContextEnvelopeFormatterLog

private val LOG = logger<AgentPromptContextEnvelopeFormatterLog>()

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
    val normalizedItems = items.map { item -> normalizeItem(item) }
    return buildContextBlock(items = normalizedItems, summary = summary, projectPath = projectPath)
  }

  fun renderContextItem(item: AgentPromptContextItem, projectPath: String? = null): String {
    return renderNormalizedItem(item = normalizeItem(item), projectPath = projectPath).trimEnd()
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
    val working = items.map { item -> normalizeItem(item) }.toMutableList()

    var serializedChars = measureContextBlockChars(items = working, summary = summary, projectPath = projectPath)
    while (serializedChars > softCapChars) {
      val updated = shrinkOneItem(items = working, excessChars = serializedChars - softCapChars)
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
      val body = current.body.trim()
      if (body.isEmpty() || body == SOFT_CAP_OMITTED_STUB) {
        continue
      }

      val partiallyTrimmed = shrinkForSoftCap(body = body, excessChars = excessChars)
      if (partiallyTrimmed != null) {
        items[index] = updateTruncation(
          item = current,
          body = partiallyTrimmed,
          reason = AgentPromptContextTruncationReason.SOFT_CAP_PARTIAL,
        )
        return true
      }

      items[index] = updateTruncation(
        item = current,
        body = SOFT_CAP_OMITTED_STUB,
        reason = AgentPromptContextTruncationReason.SOFT_CAP_OMITTED,
      )
      return true
    }
    return false
  }

  private fun shrinkForSoftCap(body: String, excessChars: Int): String? {
    val trimmedBody = body.trim()
    if (trimmedBody.length <= MIN_PARTIAL_BODY_CHARS) {
      return null
    }

    val dynamicCut = max(excessChars, trimmedBody.length / 3)
    val targetBodyLength = (trimmedBody.length - dynamicCut)
      .coerceAtLeast(MIN_PARTIAL_BODY_CHARS)
    if (targetBodyLength + SOFT_CAP_PARTIAL_SUFFIX.length >= trimmedBody.length) {
      return null
    }

    val nextBody = trimmedBody.take(targetBodyLength).trimEnd()
    if (nextBody.length <= MIN_PARTIAL_BODY_CHARS) {
      return null
    }
    return nextBody + SOFT_CAP_PARTIAL_SUFFIX
  }

  private fun normalizeItem(item: AgentPromptContextItem): AgentPromptContextItem {
    val normalizedBody = item.body.trim()
    val normalizedTruncation = normalizeTruncation(item = item, normalizedBody = normalizedBody)
    return item.copy(
      body = normalizedBody,
      truncation = normalizedTruncation,
    )
  }

  private fun normalizeTruncation(item: AgentPromptContextItem, normalizedBody: String): AgentPromptContextTruncation {
    val base = item.truncation
    val normalizedOriginal = base.originalChars
      .coerceAtLeast(normalizedBody.length)
      .coerceAtLeast(0)
    val normalizedIncluded = base.includedChars
      .coerceAtLeast(0)
      .coerceAtMost(normalizedOriginal)
      .coerceAtLeast(normalizedBody.length.coerceAtMost(normalizedOriginal))
    val reason = if (normalizedIncluded < normalizedOriginal && base.reason == AgentPromptContextTruncationReason.NONE) {
      AgentPromptContextTruncationReason.SOURCE_LIMIT
    }
    else {
      base.reason
    }
    return AgentPromptContextTruncation(
      originalChars = normalizedOriginal,
      includedChars = normalizedIncluded,
      reason = reason,
    )
  }

  private fun updateTruncation(
    item: AgentPromptContextItem,
    body: String,
    reason: AgentPromptContextTruncationReason,
  ): AgentPromptContextItem {
    val normalizedBody = body.trim()
    val originalChars = max(
      item.truncation.originalChars,
      max(normalizedBody.length, item.body.length),
    )
    val truncation = AgentPromptContextTruncation(
      originalChars = originalChars,
      includedChars = normalizedBody.length,
      reason = reason,
    )
    return item.copy(
      body = normalizedBody,
      truncation = truncation,
    )
  }

  private fun buildContextBlock(
    items: List<AgentPromptContextItem>,
    summary: AgentPromptContextEnvelopeSummary,
    projectPath: String?,
  ): String {
    val normalizedSummary = summary.normalized()
    val builder = StringBuilder(items.sumOf { item -> item.body.length } + 128)
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
      builder.append(renderNormalizedItem(item = item, projectPath = projectPath).trimEnd())
      builder.append('\n')
    }

    return builder.toString().trimEnd()
  }

  private fun renderNormalizedItem(item: AgentPromptContextItem, projectPath: String?): String {
    val renderer = AgentPromptContextRenderers.find(item.rendererId)
    if (renderer != null) {
      return try {
        renderer.renderEnvelope(
          AgentPromptEnvelopeRenderInput(
            item = item,
            projectPath = projectPath,
          )
        )
      }
      catch (t: Throwable) {
        LOG.warn("Prompt context renderer failed: ${renderer::class.java.name} (rendererId=${item.rendererId})", t)
        renderGeneric(item)
      }
    }
    return renderGeneric(item)
  }

  private fun renderGeneric(item: AgentPromptContextItem): String {
    val title = item.title?.takeIf { it.isNotBlank() } ?: "Context"
    val descriptor = "context: renderer=${item.rendererId} title=$title${renderTruncationSuffix(item)}"
    return descriptor + "\n" + appendCodeBlock(language = "text", content = item.body)
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
}
