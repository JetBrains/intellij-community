// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.engine.core.ThreadApprovalStatus
import com.intellij.agent.workbench.engine.core.ThreadToolCall
import com.intellij.agent.workbench.engine.core.ThreadToolOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal object AgentAcpToolCallPresenter {
  fun shouldRenderInTranscript(toolCall: ThreadToolCall): Boolean {
    if (!toolCall.complete) return true
    if (toolCall.approval?.status == ThreadApprovalStatus.Requested) return true
    return toolCall.isProblemToolCall()
  }

  fun create(
    toolCall: ThreadToolCall,
    textPreview: (String) -> String = { it },
    outputPreview: (String) -> String = { it },
  ): AgentAcpToolCallPresentation {
    val title = toolTitle(toolCall)
    val details = buildList {
      toolCall.command?.takeIf { it.isNotBlank() && it != title }?.let { command ->
        add(AgentAcpToolCallDetail(EngineBundle.message("acp.screen.section.command", command)))
      }
      toolCall.path?.takeIf { it.isNotBlank() }?.let { path ->
        add(AgentAcpToolCallDetail(EngineBundle.message("acp.screen.section.path", path)))
      }
      toolCall.summary?.takeIf { it.isNotBlank() }?.let { summary ->
        add(AgentAcpToolCallDetail(EngineBundle.message("acp.screen.section.summary", textPreview(summary))))
      }
      toolCall.approval?.reason?.takeIf { it.isNotBlank() }?.let { reason ->
        add(AgentAcpToolCallDetail(EngineBundle.message("acp.screen.tool.approval.reason", textPreview(reason))))
      }
      toolCall.outputText.takeIf { toolCall.shouldShowOutputDetail(it) }?.let { output ->
        add(AgentAcpToolCallDetail(EngineBundle.message("acp.screen.section.output", outputPreview(output)), monospace = true))
      }
    }
    return AgentAcpToolCallPresentation(
      title = title,
      status = toolStatus(toolCall),
      details = details,
    )
  }

  private fun toolTitle(toolCall: ThreadToolCall): String {
    return toolCall.title?.takeIf { it.isNotBlank() }
           ?: toolCall.command?.takeIf { it.isNotBlank() }
           ?: toolCall.kind?.takeIf { it.isNotBlank() }?.let(::humanizeIdentifier)
           ?: toolCall.id
  }

  private fun toolStatus(toolCall: ThreadToolCall): String? {
    val statuses = listOfNotNull(
      toolCall.status?.takeIf { it.isNotBlank() }?.let(::humanizeIdentifier),
      toolCall.approval?.status?.let(::approvalStatusText),
    ).distinctBy { it.lowercase() }

    return when (statuses.size) {
      0 -> null
      1 -> statuses.single()
      else -> EngineBundle.message("acp.screen.tool.status.withApproval", statuses[0], statuses[1])
    }
  }

  private fun approvalStatusText(status: ThreadApprovalStatus): String = when (status) {
    ThreadApprovalStatus.Requested -> EngineBundle.message("acp.screen.tool.approval.requested")
    ThreadApprovalStatus.Approved -> EngineBundle.message("acp.screen.tool.approval.approved")
    ThreadApprovalStatus.Rejected -> EngineBundle.message("acp.screen.tool.approval.rejected")
    ThreadApprovalStatus.Expired -> EngineBundle.message("acp.screen.tool.approval.expired")
  }

  private fun shouldShowOutput(outputs: List<ThreadToolOutput>, text: String): Boolean {
    if (text.isBlank()) return false
    if (outputs.all { it.stream == "structured" } && isCompactCounterMetadata(text)) return false
    return true
  }

  private fun ThreadToolCall.shouldShowOutputDetail(text: String): Boolean {
    return shouldShowOutput(output, text) && (!complete || isProblemToolCall())
  }

  internal fun isCompactCounterMetadata(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length > COMPACT_METADATA_MAX_LENGTH || '\n' in trimmed || '\r' in trimmed) return false
    val jsonObject = runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return false
    return jsonObject.values.all { value ->
      val primitive = value as? JsonPrimitive ?: return@all false
      primitive.content.toLongOrNull() != null || primitive.content.toBooleanStrictOrNull() != null
    } && jsonObject.keys.all(::isMetadataCounterKey)
  }

  private fun isMetadataCounterKey(key: String): Boolean =
    key.normalizedMetadataKey().let { normalizedKey ->
      normalizedKey == "referencecount" ||
      normalizedKey == "totalmatches" ||
      normalizedKey == "truncated" ||
      normalizedKey.endsWith("count") ||
      normalizedKey.endsWith("size") ||
      normalizedKey.endsWith("matches")
    }

  private fun String.normalizedMetadataKey(): String = replace("_", "").replace("-", "").lowercase()

  private fun String?.isProblemStatus(): Boolean = when (this?.trim()?.lowercase()) {
    "failed", "failure", "error", "errored", "cancelled", "canceled", "rejected", "expired", "timeout", "timedout", "timed_out" -> true
    else -> false
  }

  private fun ThreadToolCall.isProblemToolCall(): Boolean =
    approval?.status == ThreadApprovalStatus.Rejected ||
    approval?.status == ThreadApprovalStatus.Expired ||
    status.isProblemStatus() ||
    exitCode?.let { it != 0 } == true

  private fun humanizeIdentifier(value: String): String {
    val spaced = value.trim()
      .replace('_', ' ')
      .replace('-', ' ')
      .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
      .replace(Regex("\\s+"), " ")
    return spaced.split(' ').joinToString(" ") { word ->
      word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
    }
  }
}

internal data class AgentAcpToolCallPresentation(
  val title: String,
  val status: String?,
  val details: List<AgentAcpToolCallDetail>,
)

internal data class AgentAcpToolCallDetail(
  val text: String,
  val monospace: Boolean = false,
)

private const val COMPACT_METADATA_MAX_LENGTH = 256
