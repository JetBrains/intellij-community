// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.vcs.merge

import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentVcsMergeSessionSupportTest {
  @Test
  fun composeInitialMessageUsesNormalWorkflowPromptAndMergeContext() {
    val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(
      AgentPromptInitialMessageRequest(
        prompt = AgentVcsMergeSessionSupport.buildInitialPrompt(),
        projectPath = "/repo",
        contextItems = AgentVcsMergeSessionSupport.buildContextItems(
          fileContexts = listOf(
            supportedTextContext("src/Foo.kt"),
            unsupportedTextContext("src/Bar.kt"),
            binaryContext(),
          ),
        ),
      ),
    )

    assertThat(message).contains("Use normal IDE tools, git workflow, file edits, and any installed skills.")
    assertThat(message).contains("Success means every conflicted file leaves VCS conflict state for this merge.")
    assertThat(message).contains(
      "If this worktree is in the middle of a Git merge, rebase, or cherry-pick, stage resolved files and continue that operation when needed.",
    )
    assertThat(message).doesNotContain("Merge session id:")
    assertThat(message).doesNotContain("context: renderer=paths title=Conflicted Files")
    assertThat(message).doesNotContain("Merge helper snapshot")
    assertThat(message).contains("Conflict counts: total=2 resolved=1 unresolved=1")
    assertThat(message).contains("Theirs: 333ccc")
    assertThat(message).contains("Binary conflict: requires manual or VCS-specific workflow.")
    assertThat(message).doesNotContain("Binary conflict: no")
  }

  @Test
  fun buildContextItemsOrdersSummaryThenFiles() {
    val items = AgentVcsMergeSessionSupport.buildContextItems(
      fileContexts = listOf(
        unsupportedTextContext("z-last.txt"),
        supportedTextContext("a-first.txt"),
      ),
    )

    assertThat(items.map { it.rendererId }).containsExactly(
      AgentPromptContextRendererIds.SNIPPET,
      AgentPromptContextRendererIds.SNIPPET,
      AgentPromptContextRendererIds.SNIPPET,
    )
    assertThat(items[0].title).isEqualTo("Merge Session")
    assertThat(items.drop(1).map(AgentPromptContextItem::title)).containsExactly(
      "Merge File: a-first.txt",
      "Merge File: z-last.txt",
    )
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

  private fun supportedTextContext(path: String): AgentVcsMergePromptFileContext {
    return AgentVcsMergePromptFileContext(
      projectRelativePath = path,
      binary = false,
      totalConflicts = 2,
      resolvedConflicts = 1,
      unresolvedConflicts = 1,
      yoursTitle = "Yours",
      baseTitle = "Base",
      theirsTitle = "Theirs",
      yoursRevision = "aaa111",
      baseRevision = "bbb222",
      theirsRevision = "ccc333",
    )
  }

  private fun unsupportedTextContext(path: String): AgentVcsMergePromptFileContext {
    return AgentVcsMergePromptFileContext(
      projectRelativePath = path,
      binary = false,
      totalConflicts = null,
      resolvedConflicts = null,
      unresolvedConflicts = null,
      yoursTitle = null,
      baseTitle = null,
      theirsTitle = null,
      yoursRevision = "ddd444",
      baseRevision = "eee555",
      theirsRevision = "fff666",
    )
  }

  private fun binaryContext(): AgentVcsMergePromptFileContext {
    return AgentVcsMergePromptFileContext(
      projectRelativePath = "assets/logo.png",
      binary = true,
      totalConflicts = null,
      resolvedConflicts = null,
      unresolvedConflicts = null,
      yoursTitle = null,
      baseTitle = null,
      theirsTitle = null,
      yoursRevision = "111aaa",
      baseRevision = "222bbb",
      theirsRevision = "333ccc",
    )
  }
}
