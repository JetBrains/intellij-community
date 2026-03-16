// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import org.junit.Test
import org.junit.Assert.assertEquals

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
}
