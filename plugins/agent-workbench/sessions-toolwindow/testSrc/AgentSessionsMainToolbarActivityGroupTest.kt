// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.toolwindow.ui.AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsMainToolbarActivityGroup
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterComponent
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivitySummary
import com.intellij.agent.workbench.sessions.toolwindow.ui.buildAgentSessionsActivitySummary
import com.intellij.agent.workbench.sessions.toolwindow.ui.freshAgentSessionsActivitySummary
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.IconManager
import com.intellij.ui.BalloonLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsMainToolbarActivityGroupTest {
  @BeforeEach
  fun setUp() {
    IconLoader.activate()
    IconManager.activate(null)
  }

  @AfterEach
  fun tearDown() {
    IconManager.deactivate()
    IconLoader.deactivate()
  }

  @Test
  fun activityGroupRegisteredInLeftToolbarAfterVcsGroup() {
    val actionManager = ActionManager.getInstance()
    val actionId = AgentWorkbenchActionIds.Sessions.MainToolbar.ACTIVITY

    assertThat(actionManager.getAction(actionId))
      .isNotNull
      .isInstanceOf(AgentSessionsMainToolbarActivityGroup::class.java)

    val entries = actionManager.childActionIds("MainToolbarLeft")
    val vcsIndex = entries.requiredIndex("MainToolbarVCSGroup")
    val statusIndex = entries.requiredIndex(actionId)
    val generalIndex = entries.requiredIndex("MainToolbarGeneralActionsGroup")
    assertThat(statusIndex).isGreaterThan(vcsIndex)
    assertThat(statusIndex).isLessThan(generalIndex)
  }

  @Test
  fun activityGroupUsesGroupLocalizationKeys() {
    val actionId = AgentWorkbenchActionIds.Sessions.MainToolbar.ACTIVITY

    assertThat(AgentSessionsBundle.message("group.$actionId.text")).isEqualTo("Agent Workbench Activity")
    assertThat(AgentSessionsBundle.message("group.$actionId.description")).isEqualTo(
      "Counters for agent threads that need attention, are running, or recently completed",
    )
  }

  @Test
  fun sourceFrameGroupReusesActivityCounterComponents() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { activitySummary() },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    val counters = group.getChildren(event).filterIsInstance<AgentSessionsActivityCounterAction>()
    assertThat(counters).hasSize(3)
    runInEdtAndWait {
      assertThat(counters.map { counter -> counter.createCustomComponent(counter.templatePresentation.clone(), "MainToolbar") })
        .allMatch { component -> component is AgentSessionsActivityCounterComponent }
    }
  }

  @Test
  fun sourceFrameCountersShowAllActiveThreads() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { activitySummary() },
    )
    val counters = group.getChildren(testEventWithProject(group)).filterIsInstance<AgentSessionsActivityCounterAction>()

    val events = counters.map { counter -> testEventWithProject(counter) }
    runInEdtAndWait {
      counters.zip(events).forEach { (counter, event) -> counter.update(event) }
    }

    assertThat(events.map { event -> event.presentation.text }).containsExactly("1", "1", "1")
    assertThat(events.map { event -> event.presentation.isVisible }).containsExactly(true, true, true)
    assertThat(events.map { event -> event.presentation.isEnabled }).containsExactly(true, true, true)
  }

  @Test
  fun sourceFrameCountersUseFrameProjectBeforeProjectDataKeyIsPublished() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { activitySummary() },
    )
    val dataContext = SimpleDataContext.builder()
      .add(IdeFrame.KEY, TestIdeFrame())
      .build()
    val event = TestActionEvent.createTestEvent(group, dataContext)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    val counters = group.getChildren(event).filterIsInstance<AgentSessionsActivityCounterAction>()
    val counterEvents = counters.map { counter -> TestActionEvent.createTestEvent(counter, dataContext) }
    runInEdtAndWait {
      counters.zip(counterEvents).forEach { (counter, counterEvent) -> counter.update(counterEvent) }
    }
    assertThat(counterEvents.map { counterEvent -> counterEvent.presentation.text }).containsExactly("1", "1", "1")
    assertThat(counterEvents.map { counterEvent -> counterEvent.presentation.isVisible }).containsExactly(true, true, true)
  }

  @Test
  fun sourceFrameGroupHiddenWhenDisabledBySettings() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { false },
      activitySummary = { activitySummary() },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun sourceFrameGroupHiddenWhenThereIsNoCurrentRunAttention() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { AgentSessionsActivitySummary.EMPTY },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun sourceFrameVisibleCountersKeepZeroBucketsStableWhenAttentionExists() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { attentionOnlyActivitySummary() },
    )
    val counters = group.getChildren(testEventWithProject(group)).filterIsInstance<AgentSessionsActivityCounterAction>()

    val events = counters.map { counter -> testEventWithProject(counter) }
    runInEdtAndWait {
      counters.zip(events).forEach { (counter, event) -> counter.update(event) }
    }

    assertThat(events.map { event -> event.presentation.text }).containsExactly("1", "0", "0")
    assertThat(events.map { event -> event.presentation.isVisible }).containsExactly(true, true, true)
    assertThat(events.map { event -> event.presentation.isEnabled }).containsExactly(true, false, false)
  }

  @Test
  fun sourceFrameCountersIgnoreStaleChromeActivityRows() {
    val now = AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS + 10_000L
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { "/work/project-a" },
      isToolbarActivityEnabled = { true },
      activitySummary = { freshAgentSessionsActivitySummary(activitySummary(updatedAt = 9_999), now) },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun dedicatedFrameGroupVisibleWithoutSourceFrameActivityGates() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { true },
      sourceProjectPath = { null },
      isToolbarActivityEnabled = { false },
      activitySummary = { AgentSessionsActivitySummary.EMPTY },
      chromeActivitySummary = { AgentSessionsActivitySummary.EMPTY },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    val counters = group.getChildren(event).filterIsInstance<AgentSessionsActivityCounterAction>()
    val events = counters.map { counter -> testEventWithProject(counter) }
    runInEdtAndWait {
      counters.zip(events).forEach { (counter, counterEvent) -> counter.update(counterEvent) }
    }
    assertThat(events.map { counterEvent -> counterEvent.presentation.text }).containsExactly("0", "0", "0")
    assertThat(events.map { counterEvent -> counterEvent.presentation.isVisible }).containsExactly(true, true, true)
    assertThat(events.map { counterEvent -> counterEvent.presentation.isEnabled }).containsExactly(false, false, false)
  }

  @Test
  fun dedicatedFrameCountersUseChromeActivitySummary() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { true },
      sourceProjectPath = { null },
      isToolbarActivityEnabled = { false },
      activitySummary = { AgentSessionsActivitySummary.EMPTY },
      chromeActivitySummary = { activitySummary() },
    )
    val event = testEventWithProject(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    val counters = group.getChildren(event).filterIsInstance<AgentSessionsActivityCounterAction>()
    val events = counters.map { counter -> testEventWithProject(counter) }
    runInEdtAndWait {
      counters.zip(events).forEach { (counter, counterEvent) -> counter.update(counterEvent) }
    }
    assertThat(events.map { counterEvent -> counterEvent.presentation.text }).containsExactly("1", "1", "1")
    assertThat(events.map { counterEvent -> counterEvent.presentation.isVisible }).containsExactly(true, true, true)
    assertThat(events.map { counterEvent -> counterEvent.presentation.isEnabled }).containsExactly(true, true, true)
  }

  @Test
  fun hiddenWithoutProjectAndForSourceFrameWithoutSourcePath() {
    val group = AgentSessionsMainToolbarActivityGroup(
      isDedicatedProject = { false },
      sourceProjectPath = { null },
      isToolbarActivityEnabled = { true },
      activitySummary = { AgentSessionsActivitySummary.EMPTY },
    )
    val noProjectEvent = TestActionEvent.createTestEvent(group, SimpleDataContext.builder().build())
    val noSourcePathEvent = testEventWithProject(group)

    group.update(noProjectEvent)
    group.update(noSourcePathEvent)

    assertThat(noProjectEvent.presentation.isEnabledAndVisible).isFalse()
    assertThat(noSourcePathEvent.presentation.isEnabledAndVisible).isFalse()
  }

  private fun activitySummary(updatedAt: Long = 1_000): AgentSessionsActivitySummary {
    return buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread("attention", AgentThreadActivity.NEEDS_INPUT, updatedAt),
              thread("done", AgentThreadActivity.UNREAD, updatedAt),
            ),
          ),
          AgentProjectSessions(
            path = "/work/project-b",
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread("running", AgentThreadActivity.PROCESSING, updatedAt)),
          ),
        ),
      )
    )
  }

  private fun attentionOnlyActivitySummary(): AgentSessionsActivitySummary {
    return buildAgentSessionsActivitySummary(
      AgentSessionsState(
        projects = listOf(
          AgentProjectSessions(
            path = "/work/project-a",
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread("attention", AgentThreadActivity.NEEDS_INPUT, 1_000)),
          )
        ),
      )
    )
  }

  private fun thread(id: String, activity: AgentThreadActivity, updatedAt: Long): AgentSessionThread {
    return AgentSessionThread(
      id = id,
      title = id,
      updatedAt = updatedAt,
      archived = false,
      activity = activity,
      provider = AgentSessionProvider.CODEX,
      summaryActivity = activity,
    )
  }
}

private fun ActionManager.childActionIds(groupId: String): List<String> {
  val group = getAction(groupId) as? ActionGroup
  assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
  return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { action -> getId(action) }
}

private fun List<String>.requiredIndex(entry: String): Int {
  val index = indexOf(entry)
  assertThat(index).withFailMessage("Entry '%s' was not found in: %s", entry, this).isNotEqualTo(-1)
  return index
}

private fun testEventWithProject(action: AnAction) =
  TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
      .build(),
  )

private class TestIdeFrame : IdeFrame {
  override fun getStatusBar(): StatusBar? = null

  override fun suggestChildFrameBounds(): Rectangle = Rectangle()

  override fun getProject() = ProjectManager.getInstance().defaultProject

  override fun setFrameTitle(title: String) {
  }

  override fun getComponent(): JComponent = JPanel()

  override fun getBalloonLayout(): BalloonLayout? = null
}
