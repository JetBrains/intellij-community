// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.render

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptChipRender
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptChipRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string

class AgentPromptVcsCommitsContextRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String
    get() = AgentPromptContextRendererIds.VCS_COMMITS

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    val item = input.item
    val commits = extractCommits(item)
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
    val firstCommit = extractCommits(input.item).firstOrNull().orEmpty()
    return AgentPromptChipRender(text = composeChipText(input.item.title, firstCommit))
  }

  private fun extractCommits(item: AgentPromptContextItem): List<String> {
    val payloadCommits = item.payload.objOrNull()
      ?.array("entries")
      ?.mapNotNull { value ->
        value.objOrNull()
          ?.string("hash")
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
      }
      .orEmpty()
    if (payloadCommits.isNotEmpty()) {
      return payloadCommits
    }

    return item.body
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toList()
  }
}

private fun renderTruncationSuffix(item: AgentPromptContextItem): String {
  val truncation = item.truncation
  if (truncation.reason == AgentPromptContextTruncationReason.NONE) {
    return ""
  }
  return " [truncated=${truncation.reason.name.lowercase()} ${truncation.includedChars}/${truncation.originalChars}]"
}

private fun composeChipText(title: String?, preview: String): String {
  val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Context"
  val trimmedPreview = preview.trim()
  if (trimmedPreview.isEmpty()) {
    return resolvedTitle
  }
  val shortPreview = if (trimmedPreview.length <= 60) trimmedPreview else trimmedPreview.take(60) + "\u2026"
  return "$resolvedTitle: $shortPreview"
}
