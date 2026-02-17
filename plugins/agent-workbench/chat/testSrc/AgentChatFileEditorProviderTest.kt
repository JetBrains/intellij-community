// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class AgentChatFileEditorProviderTest {
  @Test
  fun keepsCodexResumeCommandOnVirtualFile() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-1",
      shellCommand = listOf("codex", "resume", "thread-1"),
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )

    assertEquals(listOf("codex", "resume", "thread-1"), file.shellCommand)
  }

  @Test
  fun keepsClaudeResumeCommandOnVirtualFile() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CLAUDE:session-1",
      shellCommand = listOf("claude", "--resume", "session-1"),
      threadId = "session-1",
      threadTitle = "Session One",
      subAgentId = null,
    )

    assertEquals(listOf("claude", "--resume", "session-1"), file.shellCommand)
  }

  @Test
  fun usesAgentChatProtocolAndRoundTripsDescriptor() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadId = "thread-42",
      threadTitle = "Implement parser",
      subAgentId = "alpha",
      projectHash = "hash-1",
    )

    assertEquals(AGENT_CHAT_PROTOCOL, file.fileSystem.protocol)
    val descriptor = AgentChatFileDescriptor.parsePath(file.path)
    assertNotNull(descriptor)
    assertEquals("/work/project-a", descriptor?.projectPath)
    assertEquals("CODEX:thread-42", descriptor?.threadIdentity)
    assertEquals("thread-42", descriptor?.threadId)
    assertEquals("Implement parser", descriptor?.threadTitle)
    assertEquals("alpha", descriptor?.subAgentId)
    assertEquals(listOf("codex", "resume", "thread-42"), descriptor?.shellCommand)
  }

  @Test
  fun updatesDescriptorWhenTabTitleChanges() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-7",
      shellCommand = listOf("codex", "resume", "thread-7"),
      threadId = "thread-7",
      threadTitle = "Initial title",
      subAgentId = null,
      projectHash = "hash-1",
    )

    file.updateThreadTitle("Renamed title")
    val descriptor = AgentChatFileDescriptor.parsePath(file.path)
    assertEquals("Renamed title", descriptor?.threadTitle)
  }

  @Test
  fun preservesEmptyShellCommandInDescriptor() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "CODEX:thread-empty",
      shellCommand = emptyList(),
      threadId = "thread-empty",
      threadTitle = "Thread",
      subAgentId = null,
      projectHash = "hash-1",
    )

    val descriptor = AgentChatFileDescriptor.parsePath(file.path)
    assertNotNull(descriptor)
    assertEquals(emptyList<String>(), descriptor?.shellCommand)
  }
}
