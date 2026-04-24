// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

class AgentChatRestoreNotificationServiceTest {
  @Test
  fun terminalInitReasonIncludesCommandAndPathWhenCommandIsMissing() {
    val file = testFile(shellCommand = listOf("codex", "resume", "thread-1"))
    val throwable = RuntimeException(
      "Failed to start [codex, resume, thread-1] in /work/project-a, [columns=120, rows=40], envs={PATH=/usr/bin:/bin, HOME=/Users/test}",
      IOException("No such file or directory"),
    )

    val reason = AgentChatRestoreNotificationService.buildTerminalInitializationReason(file, throwable)

    assertThat(reason).isEqualTo(
      AgentChatBundle.message(
        "chat.restore.validation.editor.init.command.missing",
        "codex",
        "/usr/bin:/bin",
      ),
    )
  }

  @Test
  fun terminalInitReasonUsesUnknownPathFallbackWhenPathIsMissing() {
    val file = testFile(shellCommand = listOf("codex", "resume", "thread-1"))
    val throwable = RuntimeException(
      "Failed to start [codex, resume, thread-1] in /work/project-a, [columns=120, rows=40], envs={HOME=/Users/test}",
      IOException("No such file or directory"),
    )

    val reason = AgentChatRestoreNotificationService.buildTerminalInitializationReason(file, throwable)

    assertThat(reason).isEqualTo(
      AgentChatBundle.message(
        "chat.restore.validation.editor.init.command.missing",
        "codex",
        AgentChatBundle.message("chat.restore.validation.editor.init.path.unknown"),
      ),
    )
  }

  @Test
  fun terminalInitReasonFallsBackToGenericMessageForNonLookupFailures() {
    val file = testFile(shellCommand = listOf("codex", "resume", "thread-1"))

    val reason = AgentChatRestoreNotificationService.buildTerminalInitializationReason(file, RuntimeException("boom"))

    assertThat(reason).isEqualTo(AgentChatBundle.message("chat.restore.validation.editor.init"))
  }

  private fun testFile(shellCommand: List<String>): AgentChatVirtualFile {
    return AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      shellCommand = shellCommand,
      threadId = "thread-1",
      threadTitle = "Thread One",
      subAgentId = null,
    )
  }
}
