// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadViewFocusServiceTest {
  @Test
  fun prefersMostRecentMatchingThreadViewFromEditorHistory() {
    val otherProject = threadViewFile("/work/project-b", "thread-b")
    val olderMatching = threadViewFile("/work/project-a", "thread-a-older")
    val newerMatching = threadViewFile("/work/project-a", "thread-a-newer")

    val file = recentOrFirstAgentThreadViewFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(otherProject, olderMatching, newerMatching),
      openFiles = arrayOf(olderMatching, newerMatching),
    )

    assertThat(file).isSameAs(newerMatching)
  }

  @Test
  fun ignoresClosedHistoryThreadViewsWhenSelectingRecentThreadView() {
    val openMatching = threadViewFile("/work/project-a", "thread-a-open")
    val closedNewerMatching = threadViewFile("/work/project-a", "thread-a-closed")

    val file = recentOrFirstAgentThreadViewFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(openMatching, closedNewerMatching),
      openFiles = arrayOf(openMatching),
    )

    assertThat(file).isSameAs(openMatching)
  }

  @Test
  fun fallsBackToFirstOpenThreadViewForProject() {
    val first = threadViewFile("/work/project-a", "thread-a-1")
    val second = threadViewFile("/work/project-a", "thread-a-2")

    val file = recentOrFirstAgentThreadViewFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(threadViewFile("/work/project-b", "thread-b")),
      openFiles = arrayOf(LightVirtualFile("readme.md"), first, second),
    )

    assertThat(file).isSameAs(first)
  }

  @Test
  fun ignoresUnrelatedProjectThreadViews() {
    val file = recentOrFirstAgentThreadViewFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(threadViewFile("/work/project-b", "thread-b")),
      openFiles = arrayOf(LightVirtualFile("readme.md"), threadViewFile("/work/project-c", "thread-c")),
    )

    assertThat(file).isNull()
  }

  @Test
  fun threadViewVirtualFilesParticipateInNonPersistentEditorHistory() {
    val file = threadViewFile("/work/project-a", "thread-a")

    assertThat(file).isInstanceOf(EditorHistoryManager.IncludeInEditorHistoryFile::class.java)
    assertThat(file.isPersistedInEditorHistory()).isFalse()
  }

  private fun threadViewFile(projectPath: String, threadId: String): AgentThreadViewVirtualFile {
    return AgentThreadViewVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CODEX:$threadId",
      shellCommand = emptyList(),
      threadId = threadId,
      threadTitle = "Thread $threadId",
      subAgentId = null,
    )
  }
}
