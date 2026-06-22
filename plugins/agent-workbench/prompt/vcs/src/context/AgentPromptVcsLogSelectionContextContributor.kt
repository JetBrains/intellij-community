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
import com.intellij.agent.workbench.prompt.core.dataContextOrNull
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.buildVcsCommitPayloadEntry
import com.intellij.agent.workbench.prompt.vcs.context.AgentPromptVcsIssueUrls.resolveIssueUrls
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.ui.render.GraphCommitCell
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable

private const val MAX_INCLUDED_SELECTION_COMMITS = 20
private const val CONTEXT_COMPONENT_DATA_ID = "contextComponent"
private const val VCS_LOG_GRAPH_TABLE_CLASS_NAME = "com.intellij.vcs.log.ui.table.VcsLogGraphTable"
private val CONTEXT_COMPONENT_DATA_KEY: DataKey<Component> = DataKey.create(CONTEXT_COMPONENT_DATA_ID)

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
    val fullContent = selectedCommits.joinToString(separator = "\n") { it.renderPromptLine() }
    val content = included.joinToString(separator = "\n") { it.renderPromptLine() }
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

    val fromVcsLogTable = extractSelectedCommitsFromVcsLogTable(dataContext)
    if (fromVcsLogTable.isNotEmpty()) {
      return normalizeSelectedCommits(fromVcsLogTable)
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

  private fun extractSelectedCommitsFromVcsLogTable(dataContext: DataContext): List<SelectedCommit> {
    val table = findVcsLogTable(CONTEXT_COMPONENT_DATA_KEY.getData(dataContext)) ?: return emptyList()
    if (table.selectedRowCount <= 0) {
      return emptyList()
    }

    val commits = ArrayList<SelectedCommit>()
    table.selectedRows.forEach { row ->
      extractSelectedCommitFromVcsLogTableRow(table, row)?.let(commits::add)
    }
    return commits
  }

  private fun findVcsLogTable(component: Component?): JTable? {
    component ?: return null
    if (component is JTable && component.javaClass.name == VCS_LOG_GRAPH_TABLE_CLASS_NAME) {
      return component
    }
    return UIUtil.uiTraverser(component)
      .filter { candidate -> candidate is JTable && candidate.javaClass.name == VCS_LOG_GRAPH_TABLE_CLASS_NAME }
      .firstOrNull() as? JTable
  }

  private fun extractSelectedCommitFromVcsLogTableRow(table: JTable, row: Int): SelectedCommit? {
    for (column in 0 until table.columnCount) {
      val commit = extractVcsLogTableCommit(table.getValueAt(row, column)) ?: continue
      return SelectedCommit(hash = commit.hash, rootPath = null, subject = commit.subject)
    }
    return null
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

  private fun SelectedCommit.renderPromptLine(): String {
    val normalizedHash = hash.trim()
    val normalizedSubject = subject?.trim()?.takeIf { it.isNotEmpty() && it != normalizedHash }
    return if (normalizedSubject == null) normalizedHash else "$normalizedHash | $normalizedSubject"
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

internal data class VcsLogTableCommitContext(
  @JvmField val hash: String,
  @JvmField val subject: String?,
)

internal fun extractVcsLogTableCommit(value: Any?): VcsLogTableCommitContext? {
  val commit = value as? GraphCommitCell.RealCommit ?: return null
  val commitId = commit.commitId ?: return null
  val hash = commitId.hash.asString().trim().takeIf { it.isNotEmpty() } ?: return null
  val subject = commit.text.trim().takeIf { it.isNotEmpty() && it != hash }
  return VcsLogTableCommitContext(hash = hash, subject = subject)
}
