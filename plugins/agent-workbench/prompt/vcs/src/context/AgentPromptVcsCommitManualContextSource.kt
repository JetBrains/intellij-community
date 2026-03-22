// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.agent.workbench.prompt.vcs.AgentPromptVcsBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import javax.swing.JList
import javax.swing.ListSelectionModel

private const val MAX_INCLUDED_SELECTION_COMMITS = 20
private const val MAX_CHOOSER_CANDIDATES = 200

internal data class CommitPickerEntry(
  @JvmField val commitIndex: Int,
  @JvmField val hash: @NlsSafe String,
  @JvmField val subject: @NlsSafe String,
  @JvmField val rootPath: String?,
  @JvmField val rootName: @NlsSafe String?,
) {
  val filterText: String
    get() = buildString {
      append(hash)
      append(' ')
      append(subject)
      rootName?.takeIf { it.isNotBlank() }?.let { append(' '); append(it) }
    }
}

private val LOG = logger<AgentPromptVcsCommitManualContextSource>()

internal class AgentPromptVcsCommitManualContextSource(
  private val projectLogAvailability: (Project) -> Boolean = VcsProjectLog::isAvailable,
  private val awaitLogManager: suspend (Project) -> VcsLogManager? = VcsProjectLog::awaitLogIsReady,
) : AgentPromptManualContextSourceBridge {
  override val sourceId: String
    get() = "manual.vcs.commits"

  override val order: Int
    get() = 50

  override fun isAvailable(project: Project): Boolean {
    return projectLogAvailability(project)
  }

  override fun getDisplayName(): String {
    return AgentPromptVcsBundle.message("manual.context.vcs.display.name")
  }

  override fun showPicker(request: AgentPromptManualContextPickerRequest) {
    if (!projectLogAvailability(request.sourceProject)) {
      request.onError(AgentPromptVcsBundle.message("manual.context.vcs.error.unavailable"))
      return
    }

    object : Task.Backgroundable(
      request.hostProject,
      AgentPromptVcsBundle.message("manual.context.vcs.loading.title"),
      true,
    ) {
      private var entries: List<CommitPickerEntry>? = null

      override fun run(indicator: ProgressIndicator) {
        entries = loadEntries(request, indicator)
      }

      override fun onSuccess() {
        val loaded = entries
        if (loaded == null) {
          request.onError(AgentPromptVcsBundle.message("manual.context.vcs.error.unavailable"))
          return
        }
        if (loaded.isEmpty()) {
          request.onError(AgentPromptVcsBundle.message("manual.context.vcs.error.empty"))
          return
        }
        showChooser(request, loaded)
      }

      override fun onThrowable(error: Throwable) {
        LOG.warn(error)
        request.onError(AgentPromptVcsBundle.message("manual.context.vcs.error.load"))
      }
    }.queue()
  }

  private fun loadEntries(
    request: AgentPromptManualContextPickerRequest,
    indicator: ProgressIndicator,
  ): List<CommitPickerEntry>? {
    val logManager = runBlockingMaybeCancellable {
      awaitLogManager(request.sourceProject)
    } ?: return null
    val dataManager = logManager.dataManager
    val eligibleRoots = resolveEligibleRootPaths(dataManager.logProviders.keys.map { root -> root.path }, request.workingProjectPath)
    if (eligibleRoots.isEmpty()) {
      return emptyList()
    }

    val commitIds = LinkedHashMap<Int, CommitId>()
    for (graphCommit in dataManager.graphData.permanentGraph.allCommits) {
      indicator.checkCanceled()
      val commitIndex = graphCommit.id
      val commitId = dataManager.getCommitId(commitIndex) ?: continue
      if (commitId.root.path !in eligibleRoots) {
        continue
      }
      commitIds[commitIndex] = commitId
      if (commitIds.size >= MAX_CHOOSER_CANDIDATES) {
        break
      }
    }
    if (commitIds.isEmpty()) {
      return emptyList()
    }

    val metadataByCommitIndex = LinkedHashMap<Int, VcsCommitMetadata>()
    dataManager.miniDetailsGetter.loadCommitsDataSynchronously(commitIds.keys.toList(), indicator) { commitIndex, metadata ->
      metadataByCommitIndex[commitIndex] = metadata
    }
    return commitIds.map { (commitIndex, commitId) ->
      val metadata = metadataByCommitIndex[commitIndex]
      CommitPickerEntry(
        commitIndex = commitIndex,
        hash = commitId.hash.asString(),
        subject = metadata?.subject?.trim()?.takeIf { it.isNotEmpty() } ?: commitId.hash.asString(),
        rootPath = commitId.root.path,
        rootName = commitId.root.name,
      )
    }
  }

  private fun showChooser(
    request: AgentPromptManualContextPickerRequest,
    entries: List<CommitPickerEntry>,
  ) {
    val selectedHashes = extractCurrentHashes(request.currentItem).toSet()
    val showRootNames = entries
      .asSequence()
      .mapNotNull(CommitPickerEntry::rootPath)
      .distinct()
      .take(2)
      .count() > 1
    val chooserList = JBList(entries).apply {
      selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      val selectedIndices = entries.mapIndexedNotNull { index, entry ->
        index.takeIf { entry.hash in selectedHashes }
      }
      if (selectedIndices.isNotEmpty()) {
        setSelectedIndices(selectedIndices.toIntArray())
      }
    }
    PopupChooserBuilder(chooserList)
      .setTitle(AgentPromptVcsBundle.message("manual.context.vcs.chooser.title"))
      .setRenderer(object : ColoredListCellRenderer<CommitPickerEntry>() {
        @Suppress("HardCodedStringLiteral")
        override fun customizeCellRenderer(
          list: JList<out CommitPickerEntry>,
          value: CommitPickerEntry?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          value ?: return
          val shortHash: @NlsSafe String = value.hash.take(8)
          append(shortHash, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          append("  ${value.subject}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
          value.rootName?.takeIf { showRootNames && it.isNotBlank() }?.let { rootName ->
            append("  $rootName", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }
      })
      .setNamerForFiltering { entry -> entry.filterText }
      .setVisibleRowCount(12)
      .setItemsChosenCallback { selectedEntries: Set<CommitPickerEntry> ->
        val selectedSet = LinkedHashSet(selectedEntries)
        val orderedSelection = entries.filter { entry -> entry in selectedSet }
        if (orderedSelection.isEmpty()) {
          return@setItemsChosenCallback
        }
        request.onSelected(buildManualVcsContextItem(orderedSelection))
      }
      .createPopup()
      .showUnderneathOf(request.anchorComponent)
  }

}

@Suppress("DuplicatedCode")
internal fun buildManualVcsContextItem(selection: List<CommitPickerEntry>): AgentPromptContextItem {
  val normalizedSelection = normalizeManualVcsSelection(selection)
  val included = normalizedSelection.take(MAX_INCLUDED_SELECTION_COMMITS)
  val fullContent = normalizedSelection.joinToString(separator = "\n") { it.hash }
  val content = included.joinToString(separator = "\n") { it.hash }
  val payloadEntries = included.map { commit ->
    val fields = linkedMapOf<String, AgentPromptPayloadValue>(
      "hash" to AgentPromptPayload.str(commit.hash),
    )
    commit.rootPath?.let { rootPath ->
      fields["rootPath"] = AgentPromptPayload.str(rootPath)
    }
    AgentPromptPayloadValue.Obj(fields)
  }
  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
    title = AgentPromptVcsBundle.message("context.vcs.manual.title"),
    body = content,
    payload = AgentPromptPayload.obj(
      "entries" to AgentPromptPayloadValue.Arr(payloadEntries),
      "selectedCount" to AgentPromptPayload.num(normalizedSelection.size),
      "includedCount" to AgentPromptPayload.num(included.size),
    ),
    itemId = "manual.vcs.commits",
    source = "manualVcs",
    truncation = AgentPromptContextTruncation(
      originalChars = fullContent.length,
      includedChars = content.length,
      reason = if (normalizedSelection.size > included.size) {
        AgentPromptContextTruncationReason.SOURCE_LIMIT
      }
      else {
        AgentPromptContextTruncationReason.NONE
      },
    ),
  )
}

internal fun normalizeManualVcsSelection(selection: List<CommitPickerEntry>): List<CommitPickerEntry> {
  if (selection.isEmpty()) {
    return emptyList()
  }

  val unique = LinkedHashMap<String, CommitPickerEntry>()
  selection.forEach { entry ->
    val normalizedHash = entry.hash.trim()
    if (normalizedHash.isEmpty()) {
      return@forEach
    }
    val normalized = if (normalizedHash == entry.hash) entry else entry.copy(hash = normalizedHash)
    unique.putIfAbsent(normalized.hash, normalized)
  }
  return unique.values.toList()
}

internal fun resolveEligibleRootPaths(
  rootPaths: Collection<String>,
  workingProjectPath: String?,
): Set<String> {
  if (workingProjectPath.isNullOrBlank()) {
    return rootPaths.toSet()
  }
  val filtered = rootPaths.filter { rootPath ->
    FileUtil.isAncestor(rootPath, workingProjectPath, false) || FileUtil.pathsEqual(workingProjectPath, rootPath)
  }
  return if (filtered.isNotEmpty()) filtered.toSet() else rootPaths.toSet()
}

internal fun extractCurrentHashes(item: AgentPromptContextItem?): List<String> {
  return item?.payload
    ?.objOrNull()
    ?.array("entries")
    ?.mapNotNull { value ->
      value.objOrNull()
        ?.string("hash")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }
    .orEmpty()
}
