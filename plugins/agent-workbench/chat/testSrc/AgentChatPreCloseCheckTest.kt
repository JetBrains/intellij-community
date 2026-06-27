// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class AgentChatPreCloseCheckTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun ignoresNonAgentChatFiles() {
    val check = AgentChatPreCloseCheck()

    assertThat(check.canCloseFiles(listOf(LightVirtualFile("plain.txt")))).isTrue()
  }

  @Test
  fun allowsNonWorkingAgentChatsWithoutDialog() {
    val check = AgentChatPreCloseCheck()
    val files = listOf(
      chatFile(AgentThreadActivity.READY),
      chatFile(AgentThreadActivity.NEEDS_INPUT),
      chatFile(AgentThreadActivity.UNREAD),
    )

    assertThat(check.canCloseFiles(files)).isTrue()
  }

  @Test
  fun cancelVetoesWorkingAgentChatClose() {
    val check = AgentChatPreCloseCheck()
    TestDialogManager.setTestDialog({ _ -> Messages.CANCEL }, disposable)

    assertThat(check.canCloseFile(chatFile(AgentThreadActivity.PROCESSING, title = "Build Fix"))).isFalse()
  }

  @Test
  fun confirmAllowsWorkingAgentChatClose() {
    val check = AgentChatPreCloseCheck()
    val messages = mutableListOf<String>()
    TestDialogManager.setTestDialog({ message ->
      messages.add(message)
      Messages.OK
    }, disposable)

    assertThat(check.canCloseFile(chatFile(AgentThreadActivity.REVIEWING, title = "Review Fix"))).isTrue()

    assertThat(messages.single()).contains("Review Fix")
  }

  @Test
  fun bulkCloseShowsOneDialogForWorkingAgentChats() {
    val check = AgentChatPreCloseCheck()
    var dialogCount = 0
    val messages = mutableListOf<String>()
    TestDialogManager.setTestDialog({ message ->
      dialogCount++
      messages.add(message)
      Messages.OK
    }, disposable)

    assertThat(check.canCloseFiles(listOf(
      chatFile(AgentThreadActivity.PROCESSING, title = "Running One"),
      chatFile(AgentThreadActivity.REVIEWING, title = "Running Two"),
      chatFile(AgentThreadActivity.READY, title = "Idle"),
    ))).isTrue()

    assertThat(dialogCount).isEqualTo(1)
    assertThat(messages.single()).contains("2 Agent Chat sessions")
  }

  private fun chatFile(
    activity: AgentThreadActivity,
    title: String = activity.name,
  ): AgentChatVirtualFile {
    return AgentChatVirtualFile(
      projectPath = "/work/project-a",
      threadIdentity = "test:${activity.name}:$title",
      shellCommand = listOf("agent"),
      threadId = "thread-${activity.name}-$title",
      threadTitle = title,
      subAgentId = null,
      threadActivity = activity,
    )
  }
}
