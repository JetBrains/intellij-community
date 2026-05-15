// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.buildVcsCommitPayloadEntry
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.resolveIssueUrls
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.util.VcsUserUtil

private const val MAX_INCLUDED_SELECTION_COMMITS = 20

private data class SelectedCommit(
  @JvmField val hash: String,
  @JvmField val rootPath: String?,
  @JvmField val subject: String? = null,
  @JvmField val author: String? = null,
  @JvmField val commitTimeMs: Long? = null,
  @JvmField val rootName: String? = null,
  @JvmField val selection: VcsLogCommitSelection? = null,
  @JvmField val selectionIndex: Int? = null,
)

internal class AgentPromptVcsLogSelectionContextContributor : AgentPromptContextContributorBridge {
  override val phase: AgentPromptContextContributorPhase
    get() = AgentPromptContextContributorPhase.INVOCATION

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
      buildVcsCommitPayloadEntry(
        hash = commit.hash,
        rootPath = commit.rootPath,
        issueUrls = commit.resolveIssueUrls(invocationData.project),
        subject = commit.subject,
        author = commit.author,
        commitTimeMs = commit.commitTimeMs,
        rootName = commit.rootName,
      )
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
      ?.let { selection ->
        selection.commits.mapIndexed { index, commit ->
          val metadata = selection.getCachedMetadata(index)
          SelectedCommit(
            hash = commit.hash.asString(),
            rootPath = commit.root.path,
            subject = metadata?.subject?.trim()?.takeIf { it.isNotEmpty() },
            author = metadata?.author?.let(VcsUserUtil::getShortPresentation)?.trim()?.takeIf { it.isNotEmpty() },
            commitTimeMs = metadata?.commitTime?.takeIf { it > 0L },
            rootName = commit.root.name,
            selection = selection,
            selectionIndex = index,
          )
        }
      }
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
      if (previous != null && !previous.hasSelection() && normalized.hasSelection()) {
        unique[normalized.hash] = normalized
      }
      if (previous != null && !previous.hasMetadata() && normalized.hasMetadata()) {
        unique[normalized.hash] = normalized
      }
    }
    return unique.values.toList()
  }

  private fun SelectedCommit.hasSelection(): Boolean {
    return selection != null && selectionIndex != null
  }

  private fun SelectedCommit.hasMetadata(): Boolean {
    return subject != null || author != null || commitTimeMs != null || rootName != null
  }

  private fun SelectedCommit.resolveIssueUrls(project: Project): List<String> {
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

  private fun VcsLogCommitSelection.getCachedMetadata(index: Int) = cachedFullDetails
                                                                      .getOrNull(index)
                                                                      ?.takeUnless { details -> details is LoadingDetails }
                                                                    ?: cachedMetadata
                                                                      .getOrNull(index)
                                                                      ?.takeUnless { metadata -> metadata is LoadingDetails }
}
