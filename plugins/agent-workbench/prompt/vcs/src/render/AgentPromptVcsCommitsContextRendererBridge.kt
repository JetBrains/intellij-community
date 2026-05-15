// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.render

import com.intellij.agent.workbench.prompt.core.AgentPromptChipRender
import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.util.text.DateFormatUtil

private const val COMMIT_CHIP_PREVIEW_MAX_LENGTH = 40

class AgentPromptVcsCommitsContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.VCS_COMMITS

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val commits = extractCommitEntries(item).map { entry -> entry.hash }
    return buildString {
      append("commits:")
      append(renderTruncationSuffix(item))
      if (commits.isNotEmpty()) {
        append('\n')
        append(commits.joinToString(separator = "\n"))
      }
    }
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    val entries = extractCommitEntries(input.item)
    val firstCommit = entries.firstOrNull()
    val preview = firstCommit?.subject ?: firstCommit?.shortHash().orEmpty()
    val totalCount = input.item.payload.objOrNull()
                       ?.number("selectedCount")
                       ?.toIntOrNull()
                     ?: 1
    return AgentPromptChipRender(
      text = composeCommitChipText(input.item.title, preview, totalCount),
      tooltipText = renderTooltip(input.item, entries),
    )
  }

  private fun extractCommitEntries(item: AgentPromptContextItem): List<CommitRenderEntry> {
    val payloadCommits = item.payload.objOrNull()
      ?.array("entries")
      ?.mapNotNull { value ->
        val entry = value.objOrNull() ?: return@mapNotNull null
        val hash = entry.string("hash")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        CommitRenderEntry(
          hash = hash,
          subject = entry.string("subject")?.trim()?.takeIf { it.isNotEmpty() },
          author = entry.string("author")?.trim()?.takeIf { it.isNotEmpty() },
          commitTimeMs = entry.number("commitTimeMs")?.toLongOrNull()?.takeIf { it > 0L },
          rootName = entry.string("rootName")?.trim()?.takeIf { it.isNotEmpty() },
        )
      }
      .orEmpty()
    if (payloadCommits.isNotEmpty()) {
      return payloadCommits
    }

    return item.body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { hash -> CommitRenderEntry(hash = hash) }
      .toList()
  }
}

private data class CommitRenderEntry(
  @JvmField val hash: String,
  @JvmField val subject: String? = null,
  @JvmField val author: String? = null,
  @JvmField val commitTimeMs: Long? = null,
  @JvmField val rootName: String? = null,
) {
  fun shortHash(): String = hash.take(8)
}

private fun renderTruncationSuffix(item: AgentPromptContextItem): String {
  val truncation = item.truncation
  if (truncation.reason == AgentPromptContextTruncationReason.NONE) {
    return ""
  }
  return " [truncated=${truncation.reason.name.lowercase()} ${truncation.includedChars}/${truncation.originalChars}]"
}

private fun renderTooltip(item: AgentPromptContextItem, entries: List<CommitRenderEntry>): String {
  return buildString {
    append("commits:")
    append(renderTruncationSuffix(item))
    if (entries.isNotEmpty()) {
      append('\n')
      append(entries.joinToString(separator = "\n") { entry -> entry.formatTooltipEntry() })
    }
  }
}

private fun CommitRenderEntry.formatTooltipEntry(): String {
  val details = ArrayList<String>()
  author?.let(details::add)
  commitTimeMs?.let { timestamp -> details += DateFormatUtil.formatPrettyDateTime(timestamp) }
  rootName?.let(details::add)

  return buildString {
    append(shortHash())
    subject?.let { text ->
      append("  ")
      append(text)
    }
    if (details.isNotEmpty()) {
      append('\n')
      append("  ")
      append(details.joinToString(separator = "  "))
    }
  }
}

private fun composeCommitChipText(title: String?, preview: String, totalCount: Int): String {
  val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Context"
  val trimmedPreview = preview.trim()
  if (trimmedPreview.isEmpty()) {
    return resolvedTitle
  }
  val shortPreview = if (trimmedPreview.length <= COMMIT_CHIP_PREVIEW_MAX_LENGTH) {
    trimmedPreview
  }
  else {
    trimmedPreview.take(COMMIT_CHIP_PREVIEW_MAX_LENGTH) + "\u2026"
  }
  val countSuffix = if (totalCount > 1) " +${totalCount - 1}" else ""
  return "$resolvedTitle: $shortPreview$countSuffix"
}
