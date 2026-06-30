// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

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
internal class AgentThreadViewPreCloseCheckTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun ignoresNonAgentThreadViewFiles() {
    val check = AgentThreadViewPreCloseCheck()

    assertThat(check.canCloseFiles(listOf(LightVirtualFile("plain.txt")))).isTrue()
  }

  @Test
  fun allowsNonWorkingAgentThreadViewsWithoutDialog() {
    val check = AgentThreadViewPreCloseCheck()
    val files = listOf(
      threadViewFile(AgentThreadActivity.READY),
      threadViewFile(AgentThreadActivity.NEEDS_INPUT),
      threadViewFile(AgentThreadActivity.UNREAD),
    )

    assertThat(check.canCloseFiles(files)).isTrue()
  }

  @Test
  fun cancelVetoesWorkingAgentThreadViewClose() {
    val check = AgentThreadViewPreCloseCheck()
    TestDialogManager.setTestDialog({ _ -> Messages.CANCEL }, disposable)

    assertThat(check.canCloseFile(threadViewFile(AgentThreadActivity.PROCESSING, title = "Build Fix"))).isFalse()
  }

  @Test
  fun confirmAllowsWorkingAgentThreadViewClose() {
    val check = AgentThreadViewPreCloseCheck()
    val messages = mutableListOf<String>()
    TestDialogManager.setTestDialog({ message ->
      messages.add(message)
      Messages.OK
    }, disposable)

    assertThat(check.canCloseFile(threadViewFile(AgentThreadActivity.REVIEWING, title = "Review Fix"))).isTrue()

    assertThat(messages.single()).contains("Review Fix")
  }

  @Test
  fun bulkCloseShowsOneDialogForWorkingAgentThreadViews() {
    val check = AgentThreadViewPreCloseCheck()
    var dialogCount = 0
    val messages = mutableListOf<String>()
    TestDialogManager.setTestDialog({ message ->
      dialogCount++
      messages.add(message)
      Messages.OK
    }, disposable)

    assertThat(check.canCloseFiles(listOf(
      threadViewFile(AgentThreadActivity.PROCESSING, title = "Running One"),
      threadViewFile(AgentThreadActivity.REVIEWING, title = "Running Two"),
      threadViewFile(AgentThreadActivity.READY, title = "Idle"),
    ))).isTrue()

    assertThat(dialogCount).isEqualTo(1)
    assertThat(messages.single()).contains("2 Agent Thread Views")
  }

  private fun threadViewFile(
    activity: AgentThreadActivity,
    title: String = activity.name,
  ): AgentThreadViewVirtualFile {
    return AgentThreadViewVirtualFile(
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
