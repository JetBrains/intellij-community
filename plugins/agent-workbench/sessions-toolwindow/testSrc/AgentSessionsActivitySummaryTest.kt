// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.statusColor
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityBucket
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterComponent
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterTone
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsStripeBadge
import com.intellij.agent.workbench.sessions.toolwindow.ui.agentSessionsActivityPopupRowText
import com.intellij.agent.workbench.sessions.toolwindow.ui.buildAgentSessionsActivitySummary
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.event.MouseEvent

@TestApplication
class AgentSessionsActivitySummaryTest {
  @Test
  fun separatesThreadsByBucket() {
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

    assertThat(summary.attentionRows.map { it.thread.id }).containsExactly("needs-input", "reviewing")
    assertThat(summary.runningRows.map { it.thread.id }).containsExactly("worktree-processing", "processing")
    assertThat(summary.doneRows.map { it.thread.id }).containsExactly("done")
    assertThat(summary.runningRows.first().path).isEqualTo("/work/project-a-feature")
    assertThat(summary.runningRows.first().locationLabel).isEqualTo("Project A / feature")
  }

  @Test
  fun rowsForBucketReturnsMatchingList() {
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

    assertThat(summary.rowsFor(AgentSessionsActivityBucket.ATTENTION)).isEmpty()
    assertThat(summary.rowsFor(AgentSessionsActivityBucket.RUNNING)).isEmpty()
    assertThat(summary.rowsFor(AgentSessionsActivityBucket.DONE).map { it.thread.id }).containsExactly("done")
  }

  @Test
  fun readyThreadsAreExcludedFromActivitySummary() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("ready", AgentThreadActivity.READY, 100),
            ),
          )
        ),
      )
    )

    assertThat(summary.attentionRows).isEmpty()
    assertThat(summary.runningRows).isEmpty()
    assertThat(summary.doneRows).isEmpty()
  }

  @Test
  fun stripeBadgePrefersAttentionOverDoneAndRunning() {
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
              thread("done", AgentThreadActivity.UNREAD, 400),
              thread("processing", AgentThreadActivity.PROCESSING, 300),
            ),
          )
        ),
      )
    )

    assertThat(summary.stripeBadge()).isEqualTo(AgentSessionsStripeBadge.ATTENTION)
  }

  @Test
  fun stripeBadgeShowsDoneWhenThereIsUnreadOutputAndNoAttention() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("done", AgentThreadActivity.UNREAD, 400),
              thread("processing", AgentThreadActivity.PROCESSING, 300),
            ),
          )
        ),
      )
    )

    assertThat(summary.stripeBadge()).isEqualTo(AgentSessionsStripeBadge.DONE)
  }

  @Test
  fun stripeBadgeIgnoresRunningReadyAndNewSessionRows() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread("processing", AgentThreadActivity.PROCESSING, 300),
              thread("ready", AgentThreadActivity.READY, 200),
              thread("new-done", AgentThreadActivity.UNREAD, 100),
            ),
          )
        ),
      )
    )

    assertThat(summary.stripeBadge()).isNull()
  }

  @Test
  fun stripeBadgeUsesAgentWorkbenchActivityColors() {
    assertThat(AgentSessionsStripeBadge.ATTENTION.color().rgb).isEqualTo(AgentThreadActivity.NEEDS_INPUT.statusColor().rgb)
    assertThat(AgentSessionsStripeBadge.DONE.color().rgb).isEqualTo(AgentThreadActivity.UNREAD.statusColor().rgb)
  }

  @Test
  fun popupRowTextIncludesTitleLocationAndRelativeTime() {
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
    assertThat(text).contains("Project A / feature")
    assertThat(text).contains("now")
  }

  @Test
  fun counterClickProvidesInputEventComponentForPopupAnchor() {
    var performedEvent: AnActionEvent? = null
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        performedEvent = e
      }
    }
    runInEdtAndWait {
      val counter = AgentSessionsActivityCounterComponent(
        action = action,
        accentColor = Color.RED,
        tone = AgentSessionsActivityCounterTone.DEFAULT,
      )
      counter.update(action.templatePresentation.clone().apply {
        text = "1"
        isEnabled = true
      })

      val clickEvent = MouseEvent(counter, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1)
      counter.mouseListeners.single().mouseClicked(clickEvent)

      assertThat(performedEvent).isNotNull
      assertThat(performedEvent?.inputEvent).isSameAs(clickEvent)
      assertThat(performedEvent?.inputEvent?.component).isSameAs(counter)
    }
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
