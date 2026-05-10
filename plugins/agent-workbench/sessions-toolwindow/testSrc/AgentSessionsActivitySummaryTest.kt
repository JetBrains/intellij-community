// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivitySectionKind
import com.intellij.agent.workbench.sessions.toolwindow.ui.agentSessionsActivityPopupRowText
import com.intellij.agent.workbench.sessions.toolwindow.ui.buildAgentSessionsActivitySummary
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsActivitySummaryTest {
  @Test
  fun separatesPrimaryAndPassiveActivities() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 500),
              thread("reviewing", AgentThreadActivity.REVIEWING, 400),
              thread("processing", AgentThreadActivity.PROCESSING, 300),
              thread("done", AgentThreadActivity.UNREAD, 200),
              thread("idle", AgentThreadActivity.READY, 100),
            ),
            worktrees = listOf(
              AgentWorktree(
                path = "/work/project-a-feature",
                name = "feature",
                branch = "feature",
                isOpen = false,
                threads = listOf(thread("worktree-processing", AgentThreadActivity.PROCESSING, 450)),
              )
            ),
          )
        ),
      )
    )

    assertThat(summary.hasPrimaryActivity).isTrue()
    assertThat(summary.attentionRows.map { it.thread.id }).containsExactly("needs-input", "reviewing")
    assertThat(summary.runningRows.map { it.thread.id }).containsExactly("worktree-processing", "processing")
    assertThat(summary.doneRows.map { it.thread.id }).containsExactly("done")
    assertThat(summary.idleRows.map { it.thread.id }).containsExactly("idle")
    assertThat(summary.runningRows.first().path).isEqualTo("/work/project-a-feature")
    assertThat(summary.runningRows.first().locationLabel).isEqualTo("Project A / feature")
    assertThat(summary.popupSections().map { it.kind }).containsExactly(
      AgentSessionsActivitySectionKind.NEEDS_ATTENTION,
      AgentSessionsActivitySectionKind.RUNNING,
      AgentSessionsActivitySectionKind.DONE,
      AgentSessionsActivitySectionKind.IDLE,
    )
  }

  @Test
  fun passiveActivitiesKeepHeaderAvailableWithoutPrimaryActivity() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("done", AgentThreadActivity.UNREAD, 200),
              thread("idle", AgentThreadActivity.READY, 100),
            ),
          )
        ),
      )
    )

    assertThat(summary.hasKnownThreads).isTrue()
    assertThat(summary.hasPrimaryActivity).isFalse()
    assertThat(summary.popupSections().map { it.kind }).containsExactly(
      AgentSessionsActivitySectionKind.DONE,
      AgentSessionsActivitySectionKind.IDLE,
    )
  }

  @Test
  fun idlePopupRowsAreLimitedToRecentThreads() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("idle-1", AgentThreadActivity.READY, 100),
              thread("idle-3", AgentThreadActivity.READY, 300),
              thread("idle-2", AgentThreadActivity.READY, 200),
            ),
          )
        ),
      ),
      maxIdleRows = 2,
    )

    assertThat(summary.idleRows.map { it.thread.id }).containsExactly("idle-3", "idle-2")
  }

  @Test
  fun popupRowTextUsesStatusLocationAndRelativeTime() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            worktrees = listOf(
              AgentWorktree(
                path = "/work/project-a-feature",
                name = "feature",
                branch = "feature",
                isOpen = false,
                threads = listOf(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 500, title = "Confirm tool call")),
              )
            ),
          )
        ),
      )
    )

    val text = agentSessionsActivityPopupRowText(summary.attentionRows.single(), now = 500)

    assertThat(text).contains("Confirm tool call")
    assertThat(text).contains("Needs input")
    assertThat(text).contains("Project A / feature")
    assertThat(text).contains("now")
  }

  private fun thread(
    id: String,
    activity: AgentThreadActivity,
    updatedAt: Long,
    title: String = id,
  ): AgentSessionThread {
    return AgentSessionThread(
      id = id,
      title = title,
      updatedAt = updatedAt,
      archived = false,
      activity = activity,
      provider = AgentSessionProvider.CODEX,
    )
  }
}
