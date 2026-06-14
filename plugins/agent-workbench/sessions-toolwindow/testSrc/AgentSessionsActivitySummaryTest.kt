// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.common.statusColor
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentation
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.toolwindow.ui.AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityBucket
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterComponent
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterTone
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivitySummary
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsMainToolbarActivityState
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsStripeBadge
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsSystemNotificationTarget
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsSystemNotificationTracker
import com.intellij.agent.workbench.sessions.toolwindow.ui.agentSessionsActivityPopupRowText
import com.intellij.agent.workbench.sessions.toolwindow.ui.buildAgentSessionsActivitySummary
import com.intellij.agent.workbench.sessions.toolwindow.ui.freshAgentSessionsActivitySummary
import com.intellij.agent.workbench.sessions.toolwindow.ui.hasLoadedActivityBaseline
import com.intellij.agent.workbench.sessions.toolwindow.ui.resolveAgentSessionsSystemNotificationThread
import com.intellij.agent.workbench.sessions.toolwindow.ui.showAgentSessionsSystemNotification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SystemNotifications
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Color
import java.awt.event.MouseEvent

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
  fun activityBaselineWaitsForWorktreeProviderLoadStates() {
    val loadingState = AgentSessionsState(
      lastUpdatedAt = 1_000L,
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          worktrees = listOf(
            AgentWorktree(
              path = "/work/project-a-feature",
              name = "feature",
              branch = "feature",
              isOpen = false,
              providerLoadStates = loadingProviderStates(AgentSessionProvider.CODEX),
            )
          ),
        )
      ),
    )

    assertThat(loadingState.hasLoadedActivityBaseline()).isFalse()
    assertThat(
      loadingState.copy(
        projects = listOf(
          loadingState.projects.single().copy(
            worktrees = listOf(
              loadingState.projects.single().worktrees.single().copy(
                providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
              )
            )
          )
        )
      ).hasLoadedActivityBaseline()
    ).isTrue()
  }

  @Test
  fun buildSummaryOverlaysSharedThreadPresentationActivity() {
    val key = checkNotNull(AgentSessionThreadPresentationKey.create("/work/project-a", AgentSessionProvider.CODEX, "done"))

    val summary = buildAgentSessionsActivitySummary(
      state(thread("done", AgentThreadActivity.READY, 100, title = "Old title")),
      presentationsByKey = mapOf(
        key to AgentSessionThreadPresentation(
          title = "Live title",
          activityReport = AgentThreadActivityReport(rowActivity = AgentThreadActivity.UNREAD, chromeActivity = AgentThreadActivity.UNREAD),
          updatedAt = 500,
        )
      ),
    )

    assertThat(summary.doneRows.map { row -> row.thread.id }).containsExactly("done")
    assertThat(summary.doneRows.single().thread.title).isEqualTo("Live title")
    assertThat(summary.doneRows.single().thread.updatedAt).isEqualTo(500)
  }

  @Test
  fun freshActivitySummaryKeepsRowsUpdatedWithinChromeFreshnessWindow() {
    val now = AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS + 10_000L
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread("fresh-attention", AgentThreadActivity.NEEDS_INPUT, 10_000),
              thread("stale-running", AgentThreadActivity.PROCESSING, 9_999),
              thread("future-done", AgentThreadActivity.UNREAD, now + 1),
            ),
          )
        ),
      )
    )

    val freshSummary = freshAgentSessionsActivitySummary(summary, now)

    assertThat(summary.runningRows.map { it.thread.id }).containsExactly("stale-running")
    assertThat(freshSummary.attentionRows.map { it.thread.id }).containsExactly("fresh-attention")
    assertThat(freshSummary.runningRows).isEmpty()
    assertThat(freshSummary.doneRows.map { it.thread.id }).containsExactly("future-done")
  }

  @Test
  fun freshActivitySummaryRemovesStaleStripeBadgeRows() {
    val now = AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS + 10_000L
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread("stale-attention", AgentThreadActivity.NEEDS_INPUT, 9_999),
              thread("stale-done", AgentThreadActivity.UNREAD, 9_999),
            ),
          )
        ),
      )
    )

    val freshSummary = freshAgentSessionsActivitySummary(summary, now)

    assertThat(summary.stripeBadge()).isEqualTo(AgentSessionsStripeBadge.ATTENTION)
    assertThat(freshSummary.attentionRows).isEmpty()
    assertThat(freshSummary.doneRows).isEmpty()
    assertThat(freshSummary.stripeBadge()).isNull()
  }

  @Test
  fun mainToolbarActivityStateIgnoresInitialLoadedAttentionBaseline() {
    val state = AgentSessionsMainToolbarActivityState()

    assertThat(state.update(summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 100)), isLoadedState = true))
      .isEqualTo(AgentSessionsActivitySummary.EMPTY)
    assertThat(state.update(summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200)), isLoadedState = true))
      .isEqualTo(AgentSessionsActivitySummary.EMPTY)
  }

  @Test
  fun mainToolbarActivityStateLatchesAttentionEnteredAfterLoadedBaseline() {
    val state = AgentSessionsMainToolbarActivityState()
    assertThat(state.update(summary(thread("needs-input", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true))
      .isEqualTo(AgentSessionsActivitySummary.EMPTY)

    val summary = summary(
      thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200),
      thread("running", AgentThreadActivity.PROCESSING, 150),
    )

    assertThat(state.update(summary, isLoadedState = true)).isEqualTo(summary)
  }

  @Test
  fun mainToolbarActivityStateClearsWhenCurrentRunAttentionLeavesBucket() {
    val state = AgentSessionsMainToolbarActivityState()
    state.update(summary(thread("needs-input", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)
    state.update(summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200)), isLoadedState = true)

    assertThat(state.update(summary(thread("needs-input", AgentThreadActivity.PROCESSING, 300)), isLoadedState = true))
      .isEqualTo(AgentSessionsActivitySummary.EMPTY)
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
  fun subAgentActivityDoesNotContributeToGlobalSummary() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              AgentSessionThread(
                id = "parent-ready",
                title = "Parent ready",
                updatedAt = 100,
                archived = false,
                activity = AgentThreadActivity.READY,
                provider = AgentSessionProvider.CODEX,
                subAgents = listOf(AgentSubAgent(id = "child-done", name = "Child done", activity = AgentThreadActivity.UNREAD)),
              ),
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
  fun nonContributingSummaryActivityKeepsRowActivityOutOfGlobalSummary() {
    val summary = buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread("sub-agent-done", AgentThreadActivity.UNREAD, 100, summaryActivity = null),
            ),
          )
        ),
      )
    )

    assertThat(summary.attentionRows).isEmpty()
    assertThat(summary.runningRows).isEmpty()
    assertThat(summary.doneRows).isEmpty()
    assertThat(summary.stripeBadge()).isNull()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
    assertThat(AgentSessionsStripeBadge.ATTENTION.color().rgb).isEqualTo(AgentThreadActivity.NEEDS_INPUT.statusColor()?.rgb)
    assertThat(AgentSessionsStripeBadge.DONE.color().rgb).isEqualTo(AgentThreadActivity.UNREAD.statusColor()?.rgb)
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
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
  fun systemNotificationTrackerWaitsForLoadedBaseline() {
    val tracker = AgentSessionsSystemNotificationTracker()

    assertThat(tracker.collectNotifications(AgentSessionsActivitySummary.EMPTY, isLoadedState = false)).isEmpty()
    assertThat(tracker.collectNotifications(summary(thread("done", AgentThreadActivity.UNREAD, 500)), isLoadedState = true)).isEmpty()
    assertThat(tracker.collectNotifications(summary(thread("done", AgentThreadActivity.UNREAD, 600)), isLoadedState = true)).isEmpty()
  }

  @Test
  fun systemNotificationTrackerReportsDoneAndAttentionTransitionsAfterLoadedBaseline() {
    val tracker = AgentSessionsSystemNotificationTracker()
    assertThat(
      tracker.collectNotifications(
        summary(
          thread("done", AgentThreadActivity.PROCESSING, 300),
          thread("needs-input", AgentThreadActivity.PROCESSING, 200),
          thread("reviewing", AgentThreadActivity.PROCESSING, 100),
        ),
        isLoadedState = true,
      )
    ).isEmpty()

    val notifications = tracker.collectNotifications(
      summary(
        thread("done", AgentThreadActivity.UNREAD, 300),
        thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200),
        thread("reviewing", AgentThreadActivity.REVIEWING, 100),
      ),
      isLoadedState = true,
    )

    assertThat(notifications.map { it.bucket }).containsExactly(
      AgentSessionsActivityBucket.ATTENTION,
      AgentSessionsActivityBucket.ATTENTION,
      AgentSessionsActivityBucket.DONE,
    )
  }

  @Test
  fun systemNotificationTrackerDoesNotDuplicateSameBucket() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(summary(thread("needs-input", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)

    assertThat(
      tracker.collectNotifications(summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200)), isLoadedState = true)
    ).hasSize(1)
    assertThat(
      tracker.collectNotifications(summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 300)), isLoadedState = true)
    ).isEmpty()
    assertThat(
      tracker.collectNotifications(summary(thread("needs-input", AgentThreadActivity.REVIEWING, 400)), isLoadedState = true)
    ).isEmpty()
  }

  @Test
  fun systemNotificationTrackerNotifiesAgainAfterLeavingBucket() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(summary(thread("done", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)

    assertThat(tracker.collectNotifications(summary(thread("done", AgentThreadActivity.UNREAD, 200)), isLoadedState = true)).hasSize(1)
    assertThat(tracker.collectNotifications(summary(thread("done", AgentThreadActivity.PROCESSING, 300)), isLoadedState = true)).isEmpty()
    assertThat(tracker.collectNotifications(summary(thread("done", AgentThreadActivity.UNREAD, 400)), isLoadedState = true)).hasSize(1)
  }

  @Test
  fun systemNotificationTrackerIgnoresNonContributingSummaryActivity() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(summary(thread("done", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)

    assertThat(
      tracker.collectNotifications(
        summary(thread("done", AgentThreadActivity.UNREAD, 200, summaryActivity = null)),
        isLoadedState = true,
      )
    ).isEmpty()
    assertThat(
      tracker.collectNotifications(
        summary(thread("done", AgentThreadActivity.UNREAD, 300, summaryActivity = AgentThreadActivity.UNREAD)),
        isLoadedState = true,
      )
    ).hasSize(1)
  }

  @Test
  fun systemNotificationTrackerIgnoresNonContributingAttentionActivity() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(summary(thread("needs-input", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)

    assertThat(
      tracker.collectNotifications(
        summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200, summaryActivity = AgentThreadActivity.READY)),
        isLoadedState = true,
      )
    ).isEmpty()
    assertThat(
      tracker.collectNotifications(
        summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 300, summaryActivity = null)),
        isLoadedState = true,
      )
    ).isEmpty()
    assertThat(
      tracker.collectNotifications(
        summary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 400, summaryActivity = AgentThreadActivity.NEEDS_INPUT)),
        isLoadedState = true,
      )
    ).hasSize(1)
  }

  @Test
  fun systemNotificationTrackerAdvancesWhenCollectedNotificationsAreDiscarded() {
    val tracker = AgentSessionsSystemNotificationTracker()

    assertThat(tracker.collectNotifications(state(thread("done", AgentThreadActivity.PROCESSING, 100)))).isEmpty()
    val discardedNotifications = tracker.collectNotifications(state(thread("done", AgentThreadActivity.UNREAD, 200)))

    assertThat(discardedNotifications).hasSize(1)
    assertThat(tracker.collectNotifications(state(thread("done", AgentThreadActivity.UNREAD, 300)))).isEmpty()

    assertThat(tracker.collectNotifications(state(thread("done", AgentThreadActivity.PROCESSING, 400)))).isEmpty()
    assertThat(tracker.collectNotifications(state(thread("done", AgentThreadActivity.UNREAD, 500)))).hasSize(1)
  }

  @Test
  fun systemNotificationTextIncludesThreadTitleAndLocation() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(worktreeSummary(thread("needs-input", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)

    val notification = tracker.collectNotifications(
      worktreeSummary(thread("needs-input", AgentThreadActivity.NEEDS_INPUT, 200, title = "Confirm tool call")),
      isLoadedState = true,
    ).single()

    assertThat(notification.title).isEqualTo("Agent Task Needs Attention")
    assertThat(notification.text).isEqualTo("\"Confirm tool call\" in Project A / feature needs attention.")
    assertThat(notification.target).isEqualTo(
      AgentSessionsSystemNotificationTarget(
        path = "/work/project-a-feature",
        provider = AgentSessionProvider.CODEX,
        threadId = "needs-input",
      )
    )
  }

  @Test
  fun systemNotificationActivationForwardsStableTarget() {
    val tracker = AgentSessionsSystemNotificationTracker()
    tracker.collectNotifications(summary(thread("done", AgentThreadActivity.PROCESSING, 100)), isLoadedState = true)
    val notification = tracker.collectNotifications(summary(thread("done", AgentThreadActivity.UNREAD, 200)), isLoadedState = true).single()
    var activationCallback: Runnable? = null
    var activatedTarget: AgentSessionsSystemNotificationTarget? = null

    val systemNotifications = object : SystemNotifications() {
      override fun notify(notificationName: String, title: String, text: String) {
        error("Activation-aware overload expected")
      }

      override fun notify(notificationName: String, title: String, text: String, onActivated: Runnable?) {
        assertThat(notificationName).isEqualTo("Agent Workbench Sessions")
        assertThat(title).isEqualTo(notification.title)
        assertThat(text).isEqualTo(notification.text)
        activationCallback = onActivated
      }
    }

    showAgentSessionsSystemNotification(
      notification = notification,
      systemNotifications = systemNotifications,
      openTarget = { target -> activatedTarget = target },
    )

    activationCallback?.run()
    assertThat(activatedTarget).isEqualTo(notification.target)
  }

  @Test
  fun systemNotificationTargetResolutionUsesStableThreadIdNotTitle() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          threads = listOf(
            thread("first", AgentThreadActivity.UNREAD, 100, title = "Same title"),
            thread("target", AgentThreadActivity.UNREAD, 200, title = "Same title"),
          ),
        )
      ),
    )

    val target = AgentSessionsSystemNotificationTarget(
      path = "/work/project-a",
      provider = AgentSessionProvider.CODEX,
      threadId = "target",
    )

    assertThat(resolveAgentSessionsSystemNotificationThread(state, target)?.id).isEqualTo("target")
  }

  @Test
  fun staleSystemNotificationTargetDoesNotResolve() {
    val state = AgentSessionsState(
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          threads = listOf(thread("existing", AgentThreadActivity.UNREAD, 100)),
        )
      ),
    )

    val target = AgentSessionsSystemNotificationTarget(
      path = "/work/project-a",
      provider = AgentSessionProvider.CODEX,
      threadId = "missing",
    )

    assertThat(resolveAgentSessionsSystemNotificationThread(state, target)).isNull()
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

  private fun summary(vararg threads: AgentSessionThread): AgentSessionsActivitySummary {
    return buildAgentSessionsActivitySummary(
      state(*threads)
    )
  }

  private fun state(vararg threads: AgentSessionThread): AgentSessionsState {
    return AgentSessionsState(
      lastUpdatedAt = 1_000L,
      projects = listOf(
        AgentProjectSessions(
          path = "/work/project-a",
          name = "Project A",
          isOpen = true,
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          threads = threads.toList(),
        )
      ),
    )
  }

  private fun worktreeSummary(vararg threads: AgentSessionThread): AgentSessionsActivitySummary {
    return buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            worktrees = listOf(
              AgentWorktree(
                path = "/work/project-a-feature",
                name = "feature",
                branch = "feature",
                isOpen = false,
                threads = threads.toList(),
              )
            ),
          )
        ),
      )
    )
  }

  private fun thread(
    id: String,
    activity: AgentThreadActivity,
    updatedAt: Long,
    title: String = id,
    summaryActivity: AgentThreadActivity? = activity,
  ): AgentSessionThread {
    return AgentSessionThread(
      id = id,
      title = title,
      updatedAt = updatedAt,
      archived = false,
      activity = activity,
      provider = AgentSessionProvider.CODEX,
      summaryActivity = summaryActivity,
    )
  }
}
