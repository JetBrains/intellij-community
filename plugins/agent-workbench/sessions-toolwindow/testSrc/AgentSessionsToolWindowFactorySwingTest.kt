// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.sessions.AgentSessionCostHintBanner
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintBanner
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintStateService
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewState
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsArchivedContextHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsArchivedRangeHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsShowActiveThreadsHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.createAgentSessionsNorthComponents
import com.intellij.agent.workbench.sessions.toolwindow.ui.createAgentSessionsTitleActions
import com.intellij.agent.workbench.sessions.toolwindow.ui.createArchivedRangeHeaderPopupGroup
import com.intellij.agent.workbench.sessions.toolwindow.ui.dispatchTreeRowOverlayQuickCreate
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.nio.file.Files

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsToolWindowFactorySwingTest {
  companion object {
    private const val SEPARATOR_MARKER = "<separator>"
  }

  @Test
  fun descriptorPointsToolWindowToSwingFactoryWithoutComposeEntries() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.toolwindow.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.toolwindow.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("factoryClass=\"com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsToolWindowFactory\"")
      .doesNotContain("Compose")
      .doesNotContain("compose")
  }

  @Test
  fun descriptorRegistersGearActionsGroup() {
    val actionManager = ActionManager.getInstance()
    val entries = actionManager.childActionEntries("AgentWorkbenchSessions.ToolWindow.GearActions")

    assertThat(entries).containsSubsequence(
      "OpenFile",
      "AgentWorkbenchSessions.ShowArchivedThreads",
      "AgentWorkbenchSessions.Refresh",
      SEPARATOR_MARKER,
      "AgentWorkbenchSessions.ToggleSessionCost",
      "AgentWorkbenchSessions.ToggleJbCentralQuotaWidget",
      "AgentWorkbenchSessions.ToggleClaudeQuotaWidget",
      "AgentWorkbenchSessions.ToggleDedicatedFrame",
    )
    assertThat(entries)
      .contains("AgentWorkbenchSessions.TogglePreventSleepWhileWorking")
      .doesNotContain("AgentWorkbenchSessions.OpenDedicatedFrame")
  }

  @Test
  fun northComponentsIncludeGlobalHintBanners(@TestDisposable disposable: Disposable) {
    val components = createAgentSessionsNorthComponents(
      project = ProjectManager.getInstance().defaultProject,
      parentDisposable = disposable,
      refreshSessions = {},
    )

    assertThat(components.any { it is AgentSessionCostHintBanner }).isTrue()
    assertThat(components.any { it is JbCentralQuotaHintBanner }).isTrue()
  }

  @Test
  fun northComponentsClearJbCentralEligibilityWhenCliIsUnavailable(@TestDisposable disposable: Disposable) {
    val hintStateService = service<JbCentralQuotaHintStateService>()
    hintStateService.loadState(JbCentralQuotaHintStateService.State(eligible = true, acknowledged = false))

    withJbCentralPath(Files.createTempDirectory("missing-jbcentral").resolve(jbCentralExecutableName())) {
      createAgentSessionsNorthComponents(
        project = ProjectManager.getInstance().defaultProject,
        parentDisposable = disposable,
        refreshSessions = {},
      )
    }

    assertThat(hintStateService.eligibleFlow.value).isFalse()
  }

  @Test
  fun northComponentsMarkJbCentralEligibleWhenCliIsAvailable(@TestDisposable disposable: Disposable) {
    val hintStateService = service<JbCentralQuotaHintStateService>()
    hintStateService.loadState(JbCentralQuotaHintStateService.State())
    val executable = Files.createTempDirectory("jbcentral-cli").resolve(jbCentralExecutableName())
    Files.writeString(executable, "stub")

    withJbCentralPath(executable) {
      createAgentSessionsNorthComponents(
        project = ProjectManager.getInstance().defaultProject,
        parentDisposable = disposable,
        refreshSessions = {},
      )
    }

    assertThat(hintStateService.eligibleFlow.value).isTrue()
  }

  @Test
  fun activeTitleActionsShowActivityCountersWithoutArchivedHeader() {
    withThreadViewState(AgentSessionThreadViewState(mode = AgentSessionThreadViewMode.ACTIVE)) {
      val actions = createAgentSessionsTitleActions()
      val counters = actions.filterIsInstance<AgentSessionsActivityCounterAction>()
      val showActiveThreads = actions.filterIsInstance<AgentSessionsShowActiveThreadsHeaderAction>().single()
      val archivedHeader = actions.filterIsInstance<AgentSessionsArchivedContextHeaderAction>().single()
      val archivedRange = actions.filterIsInstance<AgentSessionsArchivedRangeHeaderAction>().single()

      assertThat(counters).hasSize(3)
      counters.forEach { counter ->
        val event = TestActionEvent.createTestEvent(counter)
        runInEdtAndWait { counter.update(event) }
        assertThat(event.presentation.isVisible).isTrue()
      }

      val showActiveThreadsEvent = TestActionEvent.createTestEvent(showActiveThreads)
      runInEdtAndWait { showActiveThreads.update(showActiveThreadsEvent) }
      assertThat(showActiveThreadsEvent.presentation.isVisible).isFalse()

      val archivedHeaderEvent = TestActionEvent.createTestEvent(archivedHeader)
      runInEdtAndWait { archivedHeader.update(archivedHeaderEvent) }
      assertThat(archivedHeaderEvent.presentation.isVisible).isFalse()

      val archivedRangeEvent = TestActionEvent.createTestEvent(archivedRange)
      runInEdtAndWait { archivedRange.update(archivedRangeEvent) }
      assertThat(archivedRangeEvent.presentation.isVisible).isFalse()
    }
  }

  @Test
  fun archivedTitleActionsReplaceActivityCountersWithArchivedHeader() {
    withThreadViewState(
      AgentSessionThreadViewState(
        mode = AgentSessionThreadViewMode.ARCHIVED,
        archivedRangePreset = AgentSessionArchivedRangePreset.LAST_7_DAYS,
      )
    ) {
      val actions = createAgentSessionsTitleActions()
      val counters = actions.filterIsInstance<AgentSessionsActivityCounterAction>()
      val showActiveThreads = actions.filterIsInstance<AgentSessionsShowActiveThreadsHeaderAction>().single()
      val archivedHeader = actions.filterIsInstance<AgentSessionsArchivedContextHeaderAction>().single()
      val archivedRange = actions.filterIsInstance<AgentSessionsArchivedRangeHeaderAction>().single()

      counters.forEach { counter ->
        val event = TestActionEvent.createTestEvent(counter)
        runInEdtAndWait { counter.update(event) }
        assertThat(event.presentation.isVisible).isFalse()
      }

      val showActiveThreadsEvent = TestActionEvent.createTestEvent(showActiveThreads)
      runInEdtAndWait { showActiveThreads.update(showActiveThreadsEvent) }
      assertThat(showActiveThreadsEvent.presentation.isVisible).isTrue()
      assertThat(showActiveThreadsEvent.presentation.icon).isEqualTo(AllIcons.Actions.Back)
      assertThat(showActiveThreadsEvent.presentation.text).isEqualTo("Show Active Threads")
      assertThat(showActiveThreadsEvent.presentation.description).isEqualTo("Show active agent threads")

      val archivedHeaderEvent = TestActionEvent.createTestEvent(archivedHeader)
      runInEdtAndWait { archivedHeader.update(archivedHeaderEvent) }
      assertThat(archivedHeaderEvent.presentation.isVisible).isTrue()
      assertThat(archivedHeaderEvent.presentation.text).isEqualTo("Archived")
      assertThat(archivedHeaderEvent.presentation.description).isEqualTo("Showing archived threads")

      val archivedRangeEvent = TestActionEvent.createTestEvent(archivedRange)
      runInEdtAndWait { archivedRange.update(archivedRangeEvent) }
      assertThat(archivedRangeEvent.presentation.isVisible).isTrue()
      assertThat(archivedRangeEvent.presentation.text).isEqualTo("Last 7 days")
      assertThat(archivedRangeEvent.presentation.description).isEqualTo("Archived range: Last 7 days")
    }
  }

  @Test
  fun archivedHeaderBackActionSwitchesToActiveThreads() {
    withThreadViewState(AgentSessionThreadViewState(mode = AgentSessionThreadViewMode.ARCHIVED)) {
      val action = createAgentSessionsTitleActions().filterIsInstance<AgentSessionsShowActiveThreadsHeaderAction>().single()

      runInEdtAndWait { action.actionPerformed(TestActionEvent.createTestEvent(action)) }

      assertThat(service<AgentSessionThreadViewStateService>().state.value.mode).isEqualTo(AgentSessionThreadViewMode.ACTIVE)
    }
  }

  @Test
  fun archivedRangeHeaderTracksRangeStateAndContainsOnlyRangeActions() {
    withThreadViewState(AgentSessionThreadViewState(mode = AgentSessionThreadViewMode.ARCHIVED)) {
      val viewStateService = service<AgentSessionThreadViewStateService>()
      val action = createAgentSessionsTitleActions().filterIsInstance<AgentSessionsArchivedRangeHeaderAction>().single()
      viewStateService.setArchivedRangePreset(AgentSessionArchivedRangePreset.TODAY)

      val event = TestActionEvent.createTestEvent(action)
      runInEdtAndWait { action.update(event) }

      assertThat(event.presentation.text).isEqualTo("Today")
      assertThat(event.presentation.description).isEqualTo("Archived range: Today")

      val children = createArchivedRangeHeaderPopupGroup(viewStateService).getChildren(TestActionEvent.createTestEvent())
      assertThat(children).hasSize(AgentSessionArchivedRangePreset.entries.size)
      assertThat(children).allMatch { it is ToggleAction }
      assertThat(children.map { it.templatePresentation.text }).doesNotContain("Show Active Threads")
    }
  }

  @Test
  fun descriptorRegistersOpenDedicatedFrameHeaderAction() {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("AgentWorkbenchSessions.OpenDedicatedFrame")

    assertThat(action)
      .isNotNull
    assertThat(action?.javaClass?.name)
      .isEqualTo("com.intellij.agent.workbench.sessions.actions.AgentSessionsOpenDedicatedFrameAction")
    assertThat(action?.templatePresentation?.icon).isEqualTo(AllIcons.Actions.MoveToWindow)
  }

  @Test
  fun descriptorRegistersTreePopupActions() {
    val actionManager = ActionManager.getInstance()
    val entries = actionManager.childActionEntries("AgentWorkbenchSessions.TreePopup")

    assertThat(entries)
      .contains("AgentWorkbenchSessions.TreePopup.Open")
      .contains("AgentWorkbenchSessions.TreePopup.More")
      .contains("AgentWorkbenchSessions.TreePopup.NewThread")
      .contains("AgentWorkbenchSessions.TreePopup.Rename")
      .contains("AgentWorkbenchSessions.TreePopup.Archive")
      .contains("AgentWorkbenchSessions.TreePopup.Unarchive")
      .contains("CopyReferencePopupGroup")

    val newThreadIndex = entries.requiredIndex("AgentWorkbenchSessions.TreePopup.NewThread")
    val archiveIndex = entries.requiredIndex("AgentWorkbenchSessions.TreePopup.Archive")
    val unarchiveIndex = entries.requiredIndex("AgentWorkbenchSessions.TreePopup.Unarchive")
    val renameIndex = entries.requiredIndex("AgentWorkbenchSessions.TreePopup.Rename")
    val copyReferenceIndex = entries.requiredIndex("CopyReferencePopupGroup")

    assertThat(entries[newThreadIndex + 1]).isEqualTo(SEPARATOR_MARKER)
    assertThat(archiveIndex).isLessThan(unarchiveIndex)
    assertThat(unarchiveIndex).isLessThan(renameIndex)
    assertThat(renameIndex).isLessThan(copyReferenceIndex)
    assertThat(entries.subList(archiveIndex + 1, renameIndex)).doesNotContain(SEPARATOR_MARKER)

    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread"))
      .isNotNull
    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread")?.templatePresentation?.icon)
      .isEqualTo(AllIcons.General.Add)
  }

  @Test
  fun treeRowOverlayQuickCreateUsesOverlayEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var capturedPath: String? = null
    var capturedProvider: AgentSessionProvider? = null
    var capturedMode: AgentSessionLaunchMode? = null
    var capturedEntryPoint: AgentWorkbenchEntryPoint? = null
    var capturedProject = false

    dispatchTreeRowOverlayQuickCreate(
      project = project,
      path = "/work/project",
      provider = AgentSessionProvider.CODEX,
      mode = AgentSessionLaunchMode.STANDARD,
      createNewSession = { path, provider, mode, entryPoint, currentProject ->
        capturedPath = path
        capturedProvider = provider
        capturedMode = mode
        capturedEntryPoint = entryPoint
        capturedProject = currentProject === project
      },
    )

    assertThat(capturedPath).isEqualTo("/work/project")
    assertThat(capturedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(capturedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(capturedEntryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY)
    assertThat(capturedProject).isTrue()
  }

  private fun ActionManager.childActionEntries(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return flattenEntries(checkNotNull(group).getChildren(TestActionEvent.createTestEvent()))
  }

  private fun flattenEntries(actions: Array<AnAction>): List<String> {
    return actions.mapNotNull { action ->
      when (action) {
        is Separator -> SEPARATOR_MARKER
        else -> ActionManager.getInstance().getId(action)
      }
    }
  }

  private fun List<String>.requiredIndex(entry: String): Int {
    val index = indexOf(entry)
    assertThat(index).withFailMessage("Entry '%s' is missing from %s", entry, this).isGreaterThanOrEqualTo(0)
    return index
  }

  private fun withThreadViewState(state: AgentSessionThreadViewState, action: () -> Unit) {
    val service = service<AgentSessionThreadViewStateService>()
    val previousState = service.state.value
    service.setArchivedRangePreset(state.archivedRangePreset)
    service.setMode(state.mode)
    try {
      action()
    }
    finally {
      service.setArchivedRangePreset(previousState.archivedRangePreset)
      service.setMode(previousState.mode)
    }
  }
}

private fun jbCentralExecutableName(): String {
  return if (SystemInfoRt.isWindows) "jbcentral.exe" else "jbcentral"
}

private fun withJbCentralPath(path: java.nio.file.Path, action: () -> Unit) {
  val propertyName = "agent.workbench.sessions.jbcentral.path"
  val previous = System.getProperty(propertyName)
  System.setProperty(propertyName, path.toAbsolutePath().toString())
  try {
    action()
  }
  finally {
    if (previous == null) {
      System.clearProperty(propertyName)
    }
    else {
      System.setProperty(propertyName, previous)
    }
  }
}
