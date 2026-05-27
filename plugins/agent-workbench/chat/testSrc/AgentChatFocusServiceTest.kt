// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatFocusServiceTest {
  @Test
  fun prefersMostRecentMatchingChatFromEditorHistory() {
    val otherProject = chatFile("/work/project-b", "thread-b")
    val olderMatching = chatFile("/work/project-a", "thread-a-older")
    val newerMatching = chatFile("/work/project-a", "thread-a-newer")

    val file = recentOrFirstAgentChatFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(otherProject, olderMatching, newerMatching),
      openFiles = arrayOf(olderMatching, newerMatching),
    )

    assertThat(file).isSameAs(newerMatching)
  }

  @Test
  fun ignoresClosedHistoryChatsWhenSelectingRecentChat() {
    val openMatching = chatFile("/work/project-a", "thread-a-open")
    val closedNewerMatching = chatFile("/work/project-a", "thread-a-closed")

    val file = recentOrFirstAgentChatFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(openMatching, closedNewerMatching),
      openFiles = arrayOf(openMatching),
    )

    assertThat(file).isSameAs(openMatching)
  }

  @Test
  fun fallsBackToFirstOpenChatForProject() {
    val first = chatFile("/work/project-a", "thread-a-1")
    val second = chatFile("/work/project-a", "thread-a-2")

    val file = recentOrFirstAgentChatFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(chatFile("/work/project-b", "thread-b")),
      openFiles = arrayOf(LightVirtualFile("readme.md"), first, second),
    )

    assertThat(file).isSameAs(first)
  }

  @Test
  fun ignoresUnrelatedProjectChats() {
    val file = recentOrFirstAgentChatFile(
      projectPath = "/work/project-a",
      historyFiles = listOf(chatFile("/work/project-b", "thread-b")),
      openFiles = arrayOf(LightVirtualFile("readme.md"), chatFile("/work/project-c", "thread-c")),
    )

    assertThat(file).isNull()
  }

  @Test
  fun chatVirtualFilesParticipateInNonPersistentEditorHistory() {
    val file = chatFile("/work/project-a", "thread-a")

    assertThat(file).isInstanceOf(EditorHistoryManager.IncludeInEditorHistoryFile::class.java)
    assertThat(file.isPersistedInEditorHistory()).isFalse()
  }

  private fun chatFile(projectPath: String, threadId: String): AgentChatVirtualFile {
    return AgentChatVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CODEX:$threadId",
      shellCommand = emptyList(),
      threadId = threadId,
      threadTitle = "Thread $threadId",
      subAgentId = null,
    )
  }
}
