// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.sessions.AgentSessionCostHintBanner
import com.intellij.agent.workbench.sessions.AgentSessionCostPresentationSettings
import com.intellij.agent.workbench.sessions.AgentSessionCostHintStateService
import com.intellij.agent.workbench.sessions.ScriptedSessionSource
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintBanner
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaHintStateService
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.service.AgentSessionsToolWindowVisibilityService
import com.intellij.agent.workbench.sessions.openTestProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewState
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.agent.workbench.sessions.thread
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsArchivedContextHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsArchivedRangeHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsShowActiveThreadsHeaderAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsToolWindowFactory
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsToolWindowPanel
import com.intellij.agent.workbench.sessions.toolwindow.ui.createAgentSessionsNorthComponents
import com.intellij.agent.workbench.sessions.toolwindow.ui.createAgentSessionsTitleActions
import com.intellij.agent.workbench.sessions.toolwindow.ui.createArchivedRangeHeaderPopupGroup
import com.intellij.agent.workbench.sessions.waitForCondition
import com.intellij.agent.workbench.sessions.withRegisteredTestService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsToolWindowFactorySwingTest {
  companion object {
    private const val SEPARATOR_MARKER = "<separator>"
  }

  @TempDir
  lateinit var tempDir: Path

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

    withJbCentralPath(tempDir.resolve("missing-jbcentral").resolve(jbCentralExecutableName())) {
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
    val executable = tempDir.resolve("jbcentral-cli").resolve(jbCentralExecutableName())
    Files.createDirectories(executable.parent)
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
  fun sessionCostHintBannerRequestsParentRefreshWhenAcknowledged() = runBlocking {
    withSessionCostPresentationDisabled {
      val hintStateService = AgentSessionCostHintStateService().apply { markEligible() }
      assertBannerRequestsParentRefreshAfterHide(
        bannerFactory = { AgentSessionCostHintBanner(hintStateService) },
        hideBanner = { hintStateService.acknowledge() },
      )
    }
  }

  @Test
  fun jbCentralQuotaHintBannerRequestsParentRefreshWhenAcknowledged() = runBlocking {
    val executable = tempDir.resolve("jbcentral-cli").resolve(jbCentralExecutableName())
    Files.createDirectories(executable.parent)
    Files.writeString(executable, "stub")
    val propertyName = "agent.workbench.sessions.jbcentral.path"
    val previous = System.getProperty(propertyName)
    System.setProperty(propertyName, executable.toAbsolutePath().toString())
    try {
      val hintStateService = JbCentralQuotaHintStateService().apply { markEligible() }
      assertBannerRequestsParentRefreshAfterHide(
        bannerFactory = { JbCentralQuotaHintBanner(hintStateService) },
        hideBanner = { hintStateService.acknowledge() },
      )
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

  @Test
  fun coldToolWindowContentLoadsSessionsWhenInitialVisibilityIsFalse(@TestDisposable disposable: Disposable) = runBlocking {
    val refreshCount = AtomicInteger()
    val costLoadCount = AtomicInteger()
    val visibilityService = service<AgentSessionsToolWindowVisibilityService>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        refreshCount.incrementAndGet()
        if (path == PROJECT_PATH) {
          listOf(thread(id = "claude-1", updatedAt = 100, title = "Migrated Claude thread", provider = AgentSessionProvider.CLAUDE))
        }
        else {
          emptyList()
        }
      },
      loadThreadCostsProvider = { _, requestedThreads ->
        costLoadCount.incrementAndGet()
        requestedThreads.associate { thread ->
          thread.id to AgentSessionCost(amountUsd = BigDecimal.ONE, kind = AgentSessionCostKind.ESTIMATED)
        }
      },
    )

    withSessionCostPresentationEnabled {
      withRegisteredTestService(
        parentDisposable = disposable,
        sessionSourcesProvider = { listOf(source) },
        projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
        toolWindowVisibleFlow = visibilityService.visibleFlow,
      ) { service ->
        val project = ProjectManager.getInstance().defaultProject
        val manager = ColdStartToolWindowManager(project)
        project.replaceService(ToolWindowManager::class.java, manager, disposable)

        try {
          runInEdtAndWait {
            val factory = AgentSessionsToolWindowFactory()
            factory.init(manager.toolWindow)
            factory.createToolWindowContent(project, manager.toolWindow)
          }

          val threadId = SessionTreeId.Thread(PROJECT_PATH, AgentSessionProvider.CLAUDE, "claude-1")
          waitForColdStartThreadModel(
            stateProvider = { service.state.value },
            toolWindow = manager.toolWindow,
            threadId = threadId,
            refreshCount = refreshCount,
            visibilityService = visibilityService,
          )
          assertThat(refreshCount.get()).isEqualTo(1)
          assertThat(visibilityService.visibleFlow.value).isFalse()
          delay(300.milliseconds)
          assertThat(costLoadCount.get()).isZero()

          runInEdtAndWait { manager.setVisible(true) }

          waitForColdStartCostHydration(
            stateProvider = { service.state.value },
            costLoadCount = costLoadCount,
            visibilityService = visibilityService,
          )
          assertThat(refreshCount.get()).isEqualTo(1)
          assertThat(costLoadCount.get()).isEqualTo(1)
          assertThat(visibilityService.visibleFlow.value).isTrue()
        }
        finally {
          runInEdtAndWait {
            manager.toolWindow.contentManager.contents.forEach(Disposer::dispose)
          }
        }
      }
    }
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

private suspend fun assertBannerRequestsParentRefreshAfterHide(
  bannerFactory: () -> JPanel,
  hideBanner: () -> Unit,
) {
  lateinit var banner: JPanel
  lateinit var parent: RefreshTrackingPanel

  try {
    runInEdtAndWait {
      banner = bannerFactory()
      parent = RefreshTrackingPanel().apply {
        add(banner)
      }
    }

    waitForCondition { isVisibleForLayoutTest(banner) }
    runInEdtAndWait { parent.resetRefreshCounters() }

    runInEdtAndWait { hideBanner() }

    waitForCondition { !isVisibleForLayoutTest(banner) }
    waitForCondition {
      refreshCountsForTest(parent).let { it.revalidateCount > 0 && it.repaintCount > 0 }
    }
  }
  finally {
    runInEdtAndWait {
      banner.removeNotify()
      parent.removeAll()
    }
  }
}

private class ColdStartToolWindowManager(project: Project) :
  ToolWindowManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope()) {
  private val toolWindowDisposable = Disposer.newDisposable("Agent sessions cold-start toolwindow")
  private val windowInfo = WindowInfoImpl().apply {
    id = AGENT_SESSIONS_TOOL_WINDOW_ID
    anchor = ToolWindowAnchor.RIGHT
    isVisible = false
  }

  val toolWindow: ToolWindow = ToolWindowImpl(
    toolWindowManager = this,
    id = AGENT_SESSIONS_TOOL_WINDOW_ID,
    canCloseContent = false,
    dumbAware = true,
    component = null,
    parentDisposable = toolWindowDisposable,
    windowInfo = windowInfo,
    contentFactory = null,
    isAvailable = true,
    stripeTitleProvider = { "Agent Threads" },
  )

  fun setVisible(visible: Boolean) {
    windowInfo.isVisible = visible
    val publisher = project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC)
    if (visible) {
      publisher.toolWindowShown(toolWindow)
    }
    publisher.stateChanged(
      this,
      toolWindow,
      if (visible) ToolWindowManagerListener.ToolWindowManagerEventType.ShowToolWindow
      else ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow,
    )
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return if (id == AGENT_SESSIONS_TOOL_WINDOW_ID) toolWindow else super.getToolWindow(id)
  }

  override fun dispose() {
    Disposer.dispose(toolWindowDisposable)
    super.dispose()
  }
}

private suspend fun waitForColdStartThreadModel(
  stateProvider: () -> AgentSessionsState,
  toolWindow: ToolWindow,
  threadId: SessionTreeId,
  refreshCount: AtomicInteger,
  visibilityService: AgentSessionsToolWindowVisibilityService,
) {
  try {
    waitForCondition {
      refreshCount.get() >= 1 &&
      stateProvider().hasClaudeThread() &&
      toolWindow.containsSessionTreeIdInAppliedModel(threadId)
    }
  }
  catch (e: AssertionError) {
    val state = stateProvider()
    throw AssertionError(
      "Cold tool window content did not apply loaded session model in time: " +
      "refreshCount=${refreshCount.get()}, " +
      "stateHasThread=${state.hasClaudeThread()}, " +
      "appliedModelHasThread=${toolWindow.containsSessionTreeIdInAppliedModel(threadId)}, " +
      "visible=${visibilityService.visibleFlow.value}, " +
      "state=$state",
      e,
    )
  }
}

private suspend fun waitForColdStartCostHydration(
  stateProvider: () -> AgentSessionsState,
  costLoadCount: AtomicInteger,
  visibilityService: AgentSessionsToolWindowVisibilityService,
) {
  try {
    waitForCondition {
      stateProvider().claudeThreadCostAmount() == BigDecimal.ONE
    }
  }
  catch (e: AssertionError) {
    val state = stateProvider()
    throw AssertionError(
      "Cold tool window content did not hydrate visible session costs in time: " +
      "costLoadCount=${costLoadCount.get()}, " +
      "visible=${visibilityService.visibleFlow.value}, " +
      "claudeThreadCost=${state.claudeThreadCostAmount()}, " +
      "state=$state",
      e,
    )
  }
}

private fun ToolWindow.containsSessionTreeIdInAppliedModel(id: SessionTreeId): Boolean {
  var contains = false
  runInEdtAndWait {
    contains = (contentManager.contents.singleOrNull()?.component as? AgentSessionsToolWindowPanel)
      ?.containsSessionTreeIdForTest(id) == true
  }
  return contains
}

private const val PROJECT_PATH = "/work/project-a"
private const val AGENT_SESSIONS_TOOL_WINDOW_ID = "agent.workbench.sessions"

private fun AgentSessionsState.hasClaudeThread(): Boolean {
  return projects.firstOrNull { it.path == PROJECT_PATH }
    ?.threads
    ?.any { it.provider == AgentSessionProvider.CLAUDE && it.id == "claude-1" } == true
}

private fun AgentSessionsState.claudeThreadCostAmount(): BigDecimal? {
  return projects.firstOrNull { it.path == PROJECT_PATH }
    ?.threads
    ?.singleOrNull { it.provider == AgentSessionProvider.CLAUDE && it.id == "claude-1" }
    ?.cost
    ?.amountUsd
}

private fun isVisibleForLayoutTest(component: JPanel): Boolean {
  var visible = false
  runInEdtAndWait { visible = component.isVisible }
  return visible
}

private fun refreshCountsForTest(panel: RefreshTrackingPanel): RefreshCounts {
  var counts = RefreshCounts(revalidateCount = 0, repaintCount = 0)
  runInEdtAndWait {
    counts = RefreshCounts(revalidateCount = panel.revalidateCount, repaintCount = panel.repaintCount)
  }
  return counts
}

private fun jbCentralExecutableName(): String {
  return if (SystemInfoRt.isWindows) "jbcentral.exe" else "jbcentral"
}

private suspend fun withSessionCostPresentationDisabled(action: suspend () -> Unit) {
  val previous = AgentSessionCostPresentationSettings.isEnabled()
  AgentSessionCostPresentationSettings.setEnabled(false)
  try {
    action()
  }
  finally {
    AgentSessionCostPresentationSettings.setEnabled(previous)
  }
}

private suspend fun withSessionCostPresentationEnabled(action: suspend () -> Unit) {
  val previous = AgentSessionCostPresentationSettings.isEnabled()
  AgentSessionCostPresentationSettings.setEnabled(true)
  try {
    action()
  }
  finally {
    AgentSessionCostPresentationSettings.setEnabled(previous)
  }
}

private fun withJbCentralPath(path: Path, action: () -> Unit) {
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

private data class RefreshCounts(
  val revalidateCount: Int,
  val repaintCount: Int,
)

private class RefreshTrackingPanel : JPanel() {
  var revalidateCount: Int = 0
    private set

  var repaintCount: Int = 0
    private set

  override fun revalidate() {
    revalidateCount++
    super.revalidate()
  }

  override fun repaint() {
    repaintCount++
    super.repaint()
  }

  fun resetRefreshCounters() {
    revalidateCount = 0
    repaintCount = 0
  }
}
