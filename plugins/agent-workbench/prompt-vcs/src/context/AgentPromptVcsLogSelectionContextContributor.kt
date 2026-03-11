// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogDataKeys

private const val MAX_INCLUDED_SELECTION_COMMITS = 20

private data class SelectedCommit(
  @JvmField val hash: String,
  @JvmField val rootPath: String?,
)

internal class AgentPromptVcsLogSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override val order: Int
    get() = 50

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val selectedCommits = extractSelectedCommits(invocationData)
    if (selectedCommits.isEmpty()) {
      return emptyList()
    }

    val totalSelected = selectedCommits.size
    val included = selectedCommits.take(MAX_INCLUDED_SELECTION_COMMITS)
    val fullContent = selectedCommits.joinToString(separator = "\n") { it.hash }
    val content = included.joinToString(separator = "\n") { it.hash }
    if (content.isBlank()) {
      return emptyList()
    }

    val payloadEntries = included.map { commit ->
      val fields = linkedMapOf<String, AgentPromptPayloadValue>(
        "hash" to AgentPromptPayload.str(commit.hash),
      )
      commit.rootPath?.let { rootPath ->
        fields["rootPath"] = AgentPromptPayload.str(rootPath)
      }
      AgentPromptPayloadValue.Obj(fields)
    }
    val payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalSelected),
      "includedCount" to AgentPromptPayload.num(included.size),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
        title = AgentPromptVcsBundle.message("context.vcs.title"),
        body = content,
        payload = payload,
        itemId = "vcsLog.commits",
        source = "vcsLog",
        truncation = AgentPromptContextTruncation(
          originalChars = fullContent.length,
          includedChars = content.length,
          reason = if (totalSelected > included.size) {
            AgentPromptContextTruncationReason.SOURCE_LIMIT
          }
          else {
            AgentPromptContextTruncationReason.NONE
          },
        ),
      )
    )
  }

  private fun extractSelectedCommits(invocationData: AgentPromptInvocationData): List<SelectedCommit> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
    val fromCommitSelection = VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION
      .getData(dataContext)
      ?.commits
      ?.map { commit -> SelectedCommit(hash = commit.hash.asString(), rootPath = commit.root.path) }
      .orEmpty()
    if (fromCommitSelection.isNotEmpty()) {
      return normalizeSelectedCommits(fromCommitSelection)
    }

    val fromRevisionNumbers = VcsDataKeys.VCS_REVISION_NUMBERS
      .getData(dataContext)
      ?.mapNotNull(::toSelectedCommit)
      .orEmpty()
    if (fromRevisionNumbers.isNotEmpty()) {
      return normalizeSelectedCommits(fromRevisionNumbers)
    }

    val fromSingleRevision = VcsDataKeys.VCS_REVISION_NUMBER
      .getData(dataContext)
      ?.let(::toSelectedCommit)
      ?.let(::listOf)
      .orEmpty()
    return normalizeSelectedCommits(fromSingleRevision)
  }

  private fun toSelectedCommit(revisionNumber: VcsRevisionNumber): SelectedCommit? {
    val hash = revisionNumber.asString().trim()
    if (hash.isEmpty()) {
      return null
    }
    return SelectedCommit(hash = hash, rootPath = null)
  }

  private fun normalizeSelectedCommits(commits: List<SelectedCommit>): List<SelectedCommit> {
    if (commits.isEmpty()) {
      return emptyList()
    }

    val unique = LinkedHashMap<String, SelectedCommit>()
    commits.forEach { commit ->
      val normalizedHash = commit.hash.trim()
      if (normalizedHash.isEmpty()) {
        return@forEach
      }

      val normalized = if (normalizedHash == commit.hash) {
        commit
      }
      else {
        commit.copy(hash = normalizedHash)
      }
      val previous = unique.putIfAbsent(normalized.hash, normalized)
      if (previous != null && previous.rootPath == null && normalized.rootPath != null) {
        unique[normalized.hash] = normalized
      }
    }
    return unique.values.toList()
  }
}

