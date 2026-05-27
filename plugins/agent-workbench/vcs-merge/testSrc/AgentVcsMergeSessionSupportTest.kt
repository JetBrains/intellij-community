// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.number
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
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
}
