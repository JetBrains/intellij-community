// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.thread.view.AgentThreadViewTabSelection
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.service.markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread
import com.intellij.agent.workbench.sessions.service.markAgentThreadViewSelectionThreadAsReadIfUnread
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadViewUnreadAcknowledgementServiceTest {
  @Test
  fun marksUnreadSelectedProjectThreadAsRead() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection(projectPath = "$ACK_PROJECT_PATH/"),
      state = stateWithUnreadProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isTrue()
    assertThat(marks).containsExactly(ReadMark(ACK_PROJECT_PATH, AgentSessionProvider.from("codex"), ACK_THREAD_ID, 200))
  }

  @Test
  fun marksUnreadSelectedWorktreeThreadAsRead() {
    val marks = mutableListOf<ReadMark>()
    val changed = markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection(projectPath = ACK_WORKTREE_PATH),
      state = stateWithUnreadWorktreeThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isTrue()
    assertThat(marks).containsExactly(ReadMark(ACK_WORKTREE_PATH, AgentSessionProvider.from("codex"), ACK_THREAD_ID, 300))
  }

  @Test
  fun marksSelectedThreadWithUnreadChromeActivityAsRead() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection(),
      state = stateWithProjectThread(
        thread(
          activityReport = AgentThreadActivityReport(
            rowActivity = AgentThreadActivity.READY,
            chromeActivity = AgentThreadActivity.UNREAD,
          ),
          updatedAt = 250,
        )
      ),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isTrue()
    assertThat(marks).containsExactly(ReadMark(ACK_PROJECT_PATH, AgentSessionProvider.from("codex"), ACK_THREAD_ID, 250))
  }

  @Test
  fun marksUnreadSelectedThreadAsReadAfterFocusedThreadViewLosesFocus() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
      selection = selection(),
      isSelectedEditorStillFocused = false,
      state = stateWithUnreadProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isTrue()
    assertThat(marks).containsExactly(ReadMark(ACK_PROJECT_PATH, AgentSessionProvider.from("codex"), ACK_THREAD_ID, 200))
  }

  @Test
  fun doesNotMarkUnreadSelectedThreadAsReadWhenSelectedThreadViewKeepsFocus() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
      selection = selection(),
      isSelectedEditorStillFocused = true,
      state = stateWithUnreadProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isFalse()
    assertThat(marks).isEmpty()
  }

  @Test
  fun doesNotMarkThreadAsReadAfterFocusLostWithoutSelectedThreadView() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
      selection = null,
      isSelectedEditorStillFocused = false,
      state = stateWithUnreadProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isFalse()
    assertThat(marks).isEmpty()
  }

  @Test
  fun doesNotMarkReadThreadAsReadAfterFocusedThreadViewLosesFocus() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadAfterFocusLostIfUnread(
      selection = selection(),
      isSelectedEditorStillFocused = false,
      state = stateWithReadyProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isFalse()
    assertThat(marks).isEmpty()
  }

  @Test
  fun doesNotMarkThreadThatIsNotUnread() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection(),
      state = stateWithReadyProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isFalse()
    assertThat(marks).isEmpty()
  }

  @Test
  fun ignoresSelectionWithUnknownProviderIdentity() {
    val marks = mutableListOf<ReadMark>()

    val changed = markAgentThreadViewSelectionThreadAsReadIfUnread(
      selection = selection(threadIdentity = "unknown:$ACK_THREAD_ID"),
      state = stateWithUnreadProjectThread(),
      markThreadAsRead = { path, provider, threadId, updatedAt -> marks += ReadMark(path, provider, threadId, updatedAt) },
    )

    assertThat(changed).isFalse()
    assertThat(marks).isEmpty()
  }
}

private const val ACK_PROJECT_PATH = "/work/project-a"
private const val ACK_WORKTREE_PATH = "/work/project-a-feature"
private const val ACK_THREAD_ID = "thread-1"

private data class ReadMark(
  val path: String,
  val provider: AgentSessionProvider,
  val threadId: String,
  val updatedAt: Long,
)

private fun selection(
  projectPath: String = ACK_PROJECT_PATH,
  threadIdentity: String = "codex:$ACK_THREAD_ID",
): AgentThreadViewTabSelection {
  return AgentThreadViewTabSelection(
    projectPath = projectPath,
    threadIdentity = threadIdentity,
    threadId = ACK_THREAD_ID,
    subAgentId = null,
  )
}

private fun stateWithUnreadProjectThread(): AgentSessionsState {
  return stateWithProjectThread(thread(activity = AgentThreadActivity.UNREAD, updatedAt = 200))
}

private fun stateWithReadyProjectThread(): AgentSessionsState {
  return stateWithProjectThread(thread(activity = AgentThreadActivity.READY, updatedAt = 200))
}

private fun stateWithProjectThread(thread: AgentSessionThread): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = ACK_PROJECT_PATH,
        name = "Project A",
        isOpen = true,
        threads = listOf(thread),
      )
    ),
  )
}

private fun stateWithUnreadWorktreeThread(): AgentSessionsState {
  return AgentSessionsState(
    projects = listOf(
      AgentProjectSessions(
        path = ACK_PROJECT_PATH,
        name = "Project A",
        isOpen = true,
        worktrees = listOf(
          AgentWorktree(
            path = ACK_WORKTREE_PATH,
            name = "project-a-feature",
            branch = "feature",
            isOpen = true,
            threads = listOf(thread(activity = AgentThreadActivity.UNREAD, updatedAt = 300)),
          )
        ),
      )
    ),
  )
}

private fun thread(activity: AgentThreadActivity, updatedAt: Long): AgentSessionThread {
  return thread(
    activityReport = AgentThreadActivityReport(activity),
    updatedAt = updatedAt,
  )
}

private fun thread(activityReport: AgentThreadActivityReport, updatedAt: Long): AgentSessionThread {
  return AgentSessionThread(
    id = ACK_THREAD_ID,
    title = "Thread 1",
    updatedAt = updatedAt,
    archived = false,
    provider = AgentSessionProvider.from("codex"),
    activityReport = activityReport,
  )
}
