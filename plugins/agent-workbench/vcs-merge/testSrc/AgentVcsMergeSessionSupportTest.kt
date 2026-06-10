// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.service.AgentDeferredNewSessionHandle
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.merge.MergeData
import com.intellij.openapi.vcs.merge.MergeProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentVcsMergeSessionSupportTest {
  @Test
  fun composeInitialMessageUsesMinimalMergePromptWithoutConflictInventory() {
    val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = AgentVcsMergeSessionSupport.buildInitialPrompt(),
        projectPath = "/repo",
      ),
    )

    assertThat(message).contains("Determine the active conflicted files yourself using normal IDE tools, VCS integrations, or git commands.")
    assertThat(message).contains("Use normal IDE tools, git workflow, file edits, and any installed skills.")
    assertThat(message).contains("Success means this worktree leaves VCS conflict state for the current merge-related operation.")
    assertThat(message).contains(
      "If this worktree is in the middle of a Git merge, rebase, or cherry-pick, stage resolved files and continue that operation when needed.",
    )
    assertThat(message).doesNotContain("### IDE Context")
    assertThat(message).doesNotContain("Merge Session")
    assertThat(message).doesNotContain("Conflict counts:")
  }

  @Test
  fun buildSelectionHintContextItemUsesPathsRendererAndCapsIncludedPaths() {
    val item = checkNotNull(
      AgentVcsMergeSessionSupport.buildSelectionHintContextItem(
        (1..22).map { index -> "src/File$index.txt" },
      ),
    )

    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.PATHS)
    assertThat(item.title).isEqualTo("Launch Selection")
    assertThat(item.body.lines()).hasSize(20)
    val payload = item.payload.objOrNull()!!
    assertThat(payload.number("selectedCount")).isEqualTo("22")
    assertThat(payload.number("includedCount")).isEqualTo("20")
    assertThat(payload.number("fileCount")).isEqualTo("20")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
  }

  @Test
  fun buildSelectionHintContextItemReturnsNullForEmptySelection() {
    assertThat(AgentVcsMergeSessionSupport.buildSelectionHintContextItem(emptyList())).isNull()
  }

  @Test
  fun promptOnlyLaunchGateRejectsConcurrentLaunch() {
    val gate = AgentVcsMergeSessionSupport.PromptOnlyLaunchGate()

    assertThat(gate.tryStart()).isTrue()
    assertThat(gate.tryStart()).isFalse()
  }

  @Test
  fun promptOnlyLaunchGateAllowsLaunchAfterFinish() {
    val gate = AgentVcsMergeSessionSupport.PromptOnlyLaunchGate()

    assertThat(gate.tryStart()).isTrue()
    gate.finish()

    assertThat(gate.tryStart()).isTrue()
  }

  @Test
  fun noSelectedConflictsOutcomeStartsDeferredThreadAndDisposesSession(): Unit = runBlocking {
    val handle = RecordingDeferredNewSessionHandle()
    var disposed = false

    handleAgentVcsMergePreparationOutcome(
      outcome = AgentVcsMergePreparationOutcome.NoSelectedConflicts,
      deferredHandle = handle,
      initialMessageRequest = initialMessageRequestWithSelectionHint(),
      disposeSession = { disposed = true },
    )

    assertThat(handle.events).containsExactly("start")
    assertThat(handle.startedRequest?.contextItems?.single()?.body).isEqualTo("file: src/conflict.txt")
    assertThat(disposed).isTrue()
  }

  @Test
  fun autoResolvedOutcomeCompletesDeferredThreadWithoutStartingTerminal(): Unit = runBlocking {
    val handle = RecordingDeferredNewSessionHandle()
    var disposed = false

    handleAgentVcsMergePreparationOutcome(
      outcome = AgentVcsMergePreparationOutcome.AutoResolved,
      deferredHandle = handle,
      initialMessageRequest = initialMessageRequestWithSelectionHint(),
      disposeSession = { disposed = true },
    )

    assertThat(handle.events).containsExactly("complete")
    assertThat(handle.completedTitle).isEqualTo("Merge conflicts resolved")
    assertThat(handle.completedMessage).isEqualTo("All conflicts were resolved automatically.")
    assertThat(disposed).isTrue()
  }

  @Test
  fun collectExternallyResolvedFilesReturnsOnlyNonConflictingFiles() {
    val resolved = LightVirtualFile("resolved.txt", "resolved")
    val conflicted = LightVirtualFile("conflicted.txt", "conflicted")
    val bothConflicts = LightVirtualFile("both.txt", "both")
    val unchanged = LightVirtualFile("unchanged.txt", "unchanged")
    val statuses: Map<VirtualFile, FileStatus> = mapOf(
      resolved to FileStatus.MODIFIED,
      conflicted to FileStatus.MERGED_WITH_CONFLICTS,
      bothConflicts to FileStatus.MERGED_WITH_BOTH_CONFLICTS,
      unchanged to FileStatus.NOT_CHANGED,
    )

    val files = AgentVcsMergeSessionSupport.collectExternallyResolvedFiles(
      candidateFiles = listOf(resolved, conflicted, bothConflicts, unchanged),
      getStatus = { file -> statuses.getValue(file) },
    )

    assertThat(files).containsExactly(resolved, unchanged)
  }

  @Test
  fun isMergeConflictStatusRecognizesBothConflictStates() {
    assertThat(AgentVcsMergeSessionSupport.isMergeConflictStatus(FileStatus.MERGED_WITH_CONFLICTS)).isTrue()
    assertThat(AgentVcsMergeSessionSupport.isMergeConflictStatus(FileStatus.MERGED_WITH_BOTH_CONFLICTS)).isTrue()
    assertThat(AgentVcsMergeSessionSupport.isMergeConflictStatus(FileStatus.MODIFIED)).isFalse()
  }

  @Test
  fun collectSelectedConflictFilesDoesNotQueryStatusForEmptySelection() {
    val files = AgentVcsMergeSessionSupport.collectSelectedConflictFiles(emptyList()) {
      error("Empty selection must not query file status")
    }

    assertThat(files).isEmpty()
  }

  @Test
  fun collectSelectedConflictFilesReturnsOnlyStillConflictingSelection() {
    val resolved = LightVirtualFile("resolved.txt", "resolved")
    val conflicted = LightVirtualFile("conflicted.txt", "conflicted")
    val bothConflicts = LightVirtualFile("both.txt", "both")
    val statuses: Map<VirtualFile, FileStatus> = mapOf(
      resolved to FileStatus.MODIFIED,
      conflicted to FileStatus.MERGED_WITH_CONFLICTS,
      bothConflicts to FileStatus.MERGED_WITH_BOTH_CONFLICTS,
    )

    val files = AgentVcsMergeSessionSupport.collectSelectedConflictFiles(
      selectionHintFiles = listOf(resolved, conflicted, bothConflicts),
      getStatus = { file -> statuses.getValue(file) },
    )

    assertThat(files).containsExactly(conflicted, bothConflicts)
  }

  @Test
  fun collectNonBinaryConflictFilesSkipsBinaryFiles() {
    val textFile = LightVirtualFile("text.txt", "content")
    val binaryFile = LightVirtualFile("binary.bin", "content")

    val files = AgentVcsMergeSessionSupport.collectNonBinaryConflictFiles(
      files = listOf(binaryFile, textFile),
      mergeProvider = BinaryAwareMergeProvider(binaryFiles = setOf(binaryFile)),
    )

    assertThat(files).containsExactly(textFile)
  }

  @Test
  fun collectNonBinaryConflictFilesReturnsEmptyForBinaryOnlyFiles() {
    val binaryFile = LightVirtualFile("binary.bin", "content")

    val files = AgentVcsMergeSessionSupport.collectNonBinaryConflictFiles(
      files = listOf(binaryFile),
      mergeProvider = BinaryAwareMergeProvider(binaryFiles = setOf(binaryFile)),
    )

    assertThat(files).isEmpty()
  }
}

private fun initialMessageRequestWithSelectionHint(): AgentPromptInitialMessageRequest {
  return AgentPromptInitialMessageRequest(
    prompt = AgentVcsMergeSessionSupport.buildInitialPrompt(),
    projectPath = "/repo",
    contextItems = listOfNotNull(AgentVcsMergeSessionSupport.buildSelectionHintContextItem(listOf("src/conflict.txt"))),
  )
}

private class RecordingDeferredNewSessionHandle : AgentDeferredNewSessionHandle {
  override val file: VirtualFile = LightVirtualFile("agent-chat", "")
  val events = mutableListOf<String>()
  var startedRequest: AgentPromptInitialMessageRequest? = null
    private set
  var completedTitle: String? = null
    private set
  var completedMessage: String? = null
    private set

  override suspend fun start(initialMessageRequest: AgentPromptInitialMessageRequest?) {
    events += "start"
    startedRequest = initialMessageRequest
  }

  override suspend fun completeWithoutStart(title: String, message: String?) {
    events += "complete"
    completedTitle = title
    completedMessage = message
  }

  override suspend fun fail(title: String, message: String?) {
    events += "fail"
  }
}

private class BinaryAwareMergeProvider(
  private val binaryFiles: Set<VirtualFile>,
) : MergeProvider {
  override fun loadRevisions(file: VirtualFile): MergeData {
    error("Binary skip tests must not load revisions")
  }

  override fun conflictResolvedForFile(file: VirtualFile) {
  }

  override fun isBinary(file: VirtualFile): Boolean = file in binaryFiles
}
