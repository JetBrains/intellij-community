// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.buildVcsCommitPayloadEntry
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.resolveIssueUrls
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.LoadingDetails

private const val MAX_INCLUDED_SELECTION_COMMITS = 20

private data class SelectedRevision(
  @JvmField val hash: String,
  @JvmField val rootPath: String?,
  @JvmField val selection: VcsLogCommitSelection? = null,
  @JvmField val selectionIndex: Int? = null,
)

internal class AgentPromptVcsLogSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

  override val order: Int
    get() = 50

  override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
    val selectedRevisions = extractSelectedRevisions(invocationData)
    if (selectedRevisions.isEmpty()) {
      return emptyList()
    }

    val totalSelected = selectedRevisions.size
    val included = selectedRevisions.take(MAX_INCLUDED_SELECTION_COMMITS)
    val fullContent = selectedRevisions.joinToString(separator = "\n") { it.hash }
    val content = included.joinToString(separator = "\n") { it.hash }
    if (content.isBlank()) {
      return emptyList()
    }

    val payloadEntries = included.map { commit ->
      buildVcsCommitPayloadEntry(commit.hash, commit.rootPath, commit.resolveIssueUrls(invocationData.project))
    }
    val payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(totalSelected),
      "includedCount" to AgentPromptPayload.num(included.size),
    )

    return listOf(
      AgentPromptContextItem(
        rendererId = AgentPromptContextRendererIds.VCS_REVISIONS,
        title = AgentPromptVcsBundle.message("context.vcs.title"),
        body = content,
        payload = payload,
        itemId = "vcsLog.revisions",
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

  private fun extractSelectedRevisions(invocationData: AgentPromptInvocationData): List<SelectedRevision> {
    val dataContext = invocationData.dataContextOrNull() ?: return emptyList()
    val fromCommitSelection = VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION
      .getData(dataContext)
      ?.let { selection ->
        selection.commits.mapIndexed { index, commit ->
          SelectedRevision(
            hash = commit.hash.asString(),
            rootPath = commit.root.path,
            selection = selection,
            selectionIndex = index,
          )
        }
      }
      .orEmpty()
    if (fromCommitSelection.isNotEmpty()) {
      return normalizeSelectedRevisions(fromCommitSelection)
    }

    val fromRevisionNumbers = VcsDataKeys.VCS_REVISION_NUMBERS
      .getData(dataContext)
      ?.mapNotNull(::toSelectedRevision)
      .orEmpty()
    if (fromRevisionNumbers.isNotEmpty()) {
      return normalizeSelectedRevisions(fromRevisionNumbers)
    }

    val fromSingleRevision = VcsDataKeys.VCS_REVISION_NUMBER
      .getData(dataContext)
      ?.let(::toSelectedRevision)
      ?.let(::listOf)
      .orEmpty()
    return normalizeSelectedRevisions(fromSingleRevision)
  }

  private fun toSelectedRevision(revisionNumber: VcsRevisionNumber): SelectedRevision? {
    val hash = revisionNumber.asString().trim()
    if (hash.isEmpty()) {
      return null
    }
    return SelectedRevision(hash = hash, rootPath = null)
  }

  private fun normalizeSelectedRevisions(revisions: List<SelectedRevision>): List<SelectedRevision> {
    if (revisions.isEmpty()) {
      return emptyList()
    }

    val unique = LinkedHashMap<String, SelectedRevision>()
    revisions.forEach { revision ->
      val normalizedHash = revision.hash.trim()
      if (normalizedHash.isEmpty()) {
        return@forEach
      }

      val normalized = if (normalizedHash == revision.hash) {
        revision
      }
      else {
        revision.copy(hash = normalizedHash)
      }
      val previous = unique.putIfAbsent(normalized.hash, normalized)
      if (previous != null && previous.rootPath == null && normalized.rootPath != null) {
        unique[normalized.hash] = normalized
      }
      if (previous != null && !previous.hasSelection() && normalized.hasSelection()) {
        unique[normalized.hash] = normalized
      }
    }
    return unique.values.toList()
  }

  private fun SelectedRevision.hasSelection(): Boolean {
    return selection != null && selectionIndex != null
  }

  private fun SelectedRevision.resolveIssueUrls(project: Project): List<String> {
    val commitSelection = selection ?: return emptyList()
    val index = selectionIndex ?: return emptyList()
    return resolveIssueUrls(project, extractCommitText(commitSelection, index))
  }

  private fun extractCommitText(selection: VcsLogCommitSelection, index: Int): String {
    val fullMessage = selection.cachedFullDetails
      .getOrNull(index)
      ?.takeUnless { details -> details is LoadingDetails }
      ?.fullMessage
      ?.trim()
      .orEmpty()
    if (fullMessage.isNotEmpty()) {
      return fullMessage
    }

    return selection.cachedMetadata
      .getOrNull(index)
      ?.takeUnless { metadata -> metadata is LoadingDetails }
      ?.subject
      ?.trim()
      .orEmpty()
  }
}
