// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.actions.AgentSessionsDirectPathNewThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadTarget
import com.intellij.agent.workbench.sessions.actions.AgentSessionsMainToolbarNewThreadAction
import com.intellij.agent.workbench.sessions.actions.PickerActionGroup
import com.intellij.agent.workbench.sessions.actions.QuickStartAction
import com.intellij.agent.workbench.sessions.actions.resolveAgentSessionsMainToolbarNewThreadContext
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.BadgeIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsMainToolbarNewThreadActionsTest {
  @BeforeEach
  fun clearProviderAvailabilityCache() {
    ProjectManager.getInstance().defaultProject.service<AgentSessionProviderAvailabilityService>().clearAvailabilityForTest()
  }

  @Test
  fun updateUsesQuickStartProviderTitleBadgeIconAndParameterizedDescription() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.YOLO },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isInstanceOf(BadgeIcon::class.java)
    assertThat(event.presentation.text).isEqualTo(AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.NewThreadQuick.text",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
    ))
    assertThat(event.presentation.description)
      .contains(AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"))
      .contains("toolbar-project")
  }

  @Test
  fun updateFallsBackToAddIconAndEmptyDescriptionWhenNoLastUsedProvider() {
    val context = newThreadContext()
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { null },
      lastUsedLaunchMode = { null },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isEqualTo(AllIcons.General.Add)
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.empty.description"))
  }

  @Test
  fun updateDoesNotProbeProviderCliAvailability() {
    val context = newThreadContext()
    var cliChecks = 0
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      onCliAvailable = { cliChecks++ },
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val event = TestActionEvent.createTestEvent(action)
    context.project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      mapOf(AgentSessionProvider.CODEX to true),
    )

    action.update(event)

    assertThat(cliChecks).isZero()
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo(AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.NewThreadQuick.text",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
    ))
  }

  @Test
  fun updateUsesProviderSpecificQuickStartTextAndTargetDescription() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val terminalBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.TERMINAL,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLabelKeyOverride = "toolwindow.action.new.session.terminal",
      quickStartActionTextKey = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.text",
      quickStartActionDescriptionKey = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.description",
      quickStartActionTargetDescriptionKey = "action.AgentWorkbenchSessions.NewTerminalSessionQuick.target.description",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(terminalBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.TERMINAL },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewTerminalSessionQuick.text"))
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewTerminalSessionQuick.target.description",
                                             "Terminal Session",
                                             "toolbar-project"))
  }

  @Test
  fun updateSessionUsesStableQuickStartAction() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val firstMainAction = action.getMainAction(TestActionEvent.createTestEvent(action))
    val rootGroup = DefaultActionGroup(action)

    val visibleActions = timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        Utils.expandActionGroupSuspend(
          rootGroup,
          PresentationFactory(),
          DataContext.EMPTY_CONTEXT,
          ActionPlaces.MAIN_TOOLBAR,
          ActionUiKind.TOOLBAR,
          false,
        )
      }
    }

    assertThat(visibleActions).contains(action)
    assertThat(action.getMainAction(TestActionEvent.createTestEvent(action))).isSameAs(firstMainAction)
  }

  @Test
  fun getMainActionReturnsQuickStartForDirectTargetWithLastUsedProvider() {
    val path = "/tmp/toolbar-project"
    val context = newThreadContext(path = path)
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { capturedPath, provider, mode, project, capturedEntryPoint ->
        launchedPath = capturedPath
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
        entryPoint = capturedEntryPoint
      },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.YOLO },
    )
    val event = TestActionEvent.createTestEvent(action)

    val mainAction = action.getMainAction(event)

    assertThat(mainAction).isInstanceOf(QuickStartAction::class.java)
    val quickAction = mainAction as QuickStartAction
    assertThat(action.getMainAction(event)).isSameAs(quickAction)
    quickAction.actionPerformed(TestActionEvent.createTestEvent(quickAction))

    assertThat(launchedPath).isEqualTo(normalizeAgentWorkbenchPath(path))
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProjectName).isEqualTo(context.project.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TOOLBAR)
  }

  @Test
  fun getMainActionReturnsNullWhenNoLastUsedProviderSoClickFallsThroughToPicker() {
    val context = newThreadContext()
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { null },
      lastUsedLaunchMode = { null },
    )

    assertThat(action.getMainAction(TestActionEvent.createTestEvent(action))).isNull()
  }

  @Test
  fun directPathPrimaryActionUsesQuickStartEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var beforeActionCount = 0
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, provider, mode, _, capturedEntryPoint ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        entryPoint = capturedEntryPoint
      },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
      beforeAction = { beforeActionCount++ },
    )
    val mainAction = checkNotNull(action.getMainAction(TestActionEvent.createTestEvent(action)))

    mainAction.actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(beforeActionCount).isEqualTo(1)
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY)
  }

  @Test
  fun directPathPopupChildrenUsePopupEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var beforeActionCount = 0
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, provider, mode, _, capturedEntryPoint ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        entryPoint = capturedEntryPoint
      },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
      beforeAction = { beforeActionCount++ },
    )
    val event = TestActionEvent.createTestEvent(action)
    val childAction = action.actionGroup.getChildren(event).first { child -> child !is Separator }

    childAction.actionPerformed(TestActionEvent.createTestEvent(childAction))

    assertThat(beforeActionCount).isEqualTo(1)
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
  }

  @Test
  fun directPathActionHidesWhenTargetOrProvidersAreUnavailable() {
    val project = ProjectManager.getInstance().defaultProject
    val missingTargetAction = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { null },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = {
        listOf(TestAgentSessionProviderDescriptor(
          provider = AgentSessionProvider.CODEX,
          supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
          cliAvailable = true,
        ))
      },
    )
    val noProvidersAction = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { emptyList() },
    )
    val missingTargetEvent = TestActionEvent.createTestEvent(missingTargetAction)
    val noProvidersEvent = TestActionEvent.createTestEvent(noProvidersAction)

    missingTargetAction.update(missingTargetEvent)
    noProvidersAction.update(noProvidersEvent)

    assertThat(missingTargetEvent.presentation.isEnabledAndVisible).isFalse()
    assertThat(noProvidersEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun getMainActionReturnsQuickStartForCandidatesTargetAndClickShowsPicker() {
    val context = newThreadContext(
      projectPathCandidates = listOf(
        projectCandidate(path = "/work/repo-a", displayName = "Project A"),
        projectCandidate(path = "/tmp/repo-a", displayName = "/tmp/repo-a"),
      ),
    )
    var launched = false
    var pickerShown = 0
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> launched = true },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
      showPicker = { _, _ -> pickerShown++ },
    )
    val mainAction = action.getMainAction(TestActionEvent.createTestEvent(action))

    assertThat(mainAction).isInstanceOf(QuickStartAction::class.java)
    checkNotNull(mainAction).actionPerformed(TestActionEvent.createTestEvent(mainAction))
    assertThat(launched).isFalse()
    assertThat(pickerShown).isEqualTo(1)
  }

  @Test
  fun primaryClickUsesCachedProviderAvailabilityBeforeQuickLaunchAndFallsBackToPicker() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    var cliChecks = 0
    var launched = false
    var pickerShown = 0
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      onCliAvailable = { cliChecks++ },
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> launched = true },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
      showPicker = { _, _ -> pickerShown++ },
    )
    context.project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      mapOf(AgentSessionProvider.CODEX to false),
    )
    val mainAction = action.getMainAction(TestActionEvent.createTestEvent(action))

    assertThat(mainAction).isInstanceOf(QuickStartAction::class.java)
    checkNotNull(mainAction).actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(cliChecks).isZero()
    assertThat(launched).isFalse()
    assertThat(pickerShown).isEqualTo(1)
  }

  @Test
  fun pickerGroupReturnsFlatProviderMenuForDirectTarget() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val pickerGroup = PickerActionGroup(
      resolveContext = { context },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { _, _, _, _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(pickerGroup)

    val children = pickerGroup.getChildren(event)

    val texts = children.filter { it !is Separator }.map { it.templatePresentation.text }
    assertThat(texts).contains(
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
      AgentSessionsBundle.message("toolwindow.action.new.session.claude"),
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
    )
  }

  @Test
  fun pickerGroupReturnsCandidateSubGroupsForCandidatesTarget() {
    val context = newThreadContext(
      projectPathCandidates = listOf(
        projectCandidate(path = "/work/repo-a", displayName = "Project A"),
        projectCandidate(path = "/tmp/repo-a", displayName = "/tmp/repo-a"),
      ),
    )
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val pickerGroup = PickerActionGroup(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { capturedPath, provider, mode, _, capturedEntryPoint ->
        launchedPath = capturedPath
        launchedProvider = provider
        launchedMode = mode
        entryPoint = capturedEntryPoint
      },
    )
    val event = TestActionEvent.createTestEvent(pickerGroup)

    val children = pickerGroup.getChildren(event)

    assertThat(children.map { it.templatePresentation.text }).containsExactly("Project A", "/tmp/repo-a")
    val secondProjectGroup = children.last() as ActionGroup
    val secondProjectChildren = secondProjectGroup.getChildren(event)
    val yoloAction = secondProjectChildren.first { child ->
      child !is Separator && child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }
    yoloAction.actionPerformed(TestActionEvent.createTestEvent(yoloAction))

    assertThat(launchedPath).isEqualTo("/tmp/repo-a")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TOOLBAR)
  }

  @Test
  fun updateHidesActionWhenNoProvidersAreRegistered() {
    val context = newThreadContext()
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { emptyList() },
      createNewSession = { _, _, _, _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun updateHidesActionWhenContextIsUnavailable() {
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { null },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun tooltipNamesProviderAndProjectForDirectTarget() {
    val context = newThreadContext(path = "/work/my-repo")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    val expected = AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.MainToolbar.NewThread.description",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
      "my-repo",
    )
    assertThat(event.presentation.description).isEqualTo(expected)
  }

  @Test
  fun tooltipUsesChooseProjectPlaceholderForCandidates() {
    val context = newThreadContext(
      projectPathCandidates = listOf(
        projectCandidate(path = "/work/repo-a", displayName = "Project A"),
        projectCandidate(path = "/tmp/repo-a", displayName = "/tmp/repo-a"),
      ),
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.description).contains(
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose"),
    )
  }

  @Test
  fun updateUsesDeferredTargetPlaceholderWithoutResolvingCandidates() {
    var candidatesResolved = false
    val context = AgentSessionsEditorTabNewThreadContext(
      project = ProjectManager.getInstance().defaultProject,
      resolveTarget = {
        candidatesResolved = true
        AgentSessionsEditorTabNewThreadTarget.Candidates(
          listOf(projectCandidate(path = "/work/repo-a", displayName = "Project A")),
        )
      },
      resolveTargetForUpdate = { null },
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _, _ -> },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.STANDARD },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(candidatesResolved).isFalse()
    assertThat(event.presentation.description).contains(
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose"),
    )
  }

  @Test
  fun projectMainToolbarContextPrefersSelectedChatSourcePathOverProjectBasePath() {
    val project = sourceProjectProxy()
    val event = eventWithProject(project)

    val context = resolveAgentSessionsMainToolbarNewThreadContext(
      event = event,
      isDedicatedProject = { false },
      selectedSourcePath = { "/work/chat-repo" },
    )

    val target = checkNotNull(context).target as AgentSessionsEditorTabNewThreadTarget.Direct
    assertThat(target.path).isEqualTo("/work/chat-repo")
  }

  @Test
  fun projectMainToolbarContextUsesProjectBasePathWhenNoSelectedChatSourcePathExists() {
    val project = sourceProjectProxy()
    val event = eventWithProject(project)

    val context = resolveAgentSessionsMainToolbarNewThreadContext(
      event = event,
      isDedicatedProject = { false },
      selectedSourcePath = { null },
    )

    val target = checkNotNull(context).target as AgentSessionsEditorTabNewThreadTarget.Direct
    assertThat(target.path).isEqualTo("/work/repo-a")
  }

  @Test
  fun projectMainToolbarContextPrefersEventChatContextWhenAvailable() {
    val project = sourceProjectProxy()
    val event = eventWithProject(project)

    val context = resolveAgentSessionsMainToolbarNewThreadContext(
      event = event,
      isDedicatedProject = { false },
      resolveChatContext = { editorContext() },
      selectedSourcePath = { "/work/selected-chat-repo" },
    )

    val target = checkNotNull(context).target as AgentSessionsEditorTabNewThreadTarget.Direct
    assertThat(target.path).isEqualTo("/work/event-chat-repo")
  }

  @Test
  fun dedicatedMainToolbarContextResolvesOpenProjectsLazily() {
    val event = eventWithProject(ProjectManager.getInstance().defaultProject)
    var openProjectPathsResolved = false

    val context = resolveAgentSessionsMainToolbarNewThreadContext(
      event = event,
      isDedicatedProject = { true },
      openProjectPaths = {
        openProjectPathsResolved = true
        listOf("/work/repo-a", "/tmp/repo-a")
      },
    )

    assertThat(openProjectPathsResolved).isFalse()

    val candidates = (checkNotNull(context).target as AgentSessionsEditorTabNewThreadTarget.Candidates).candidates
    assertThat(openProjectPathsResolved).isTrue()
    assertThat(candidates.map(AgentPromptProjectPathCandidate::path))
      .containsExactly("/work/repo-a", "/tmp/repo-a")
  }
}

private fun editorContext(): AgentChatEditorTabActionContext {
  val path = "/work/event-chat-repo"
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizedPath,
    tabKey = "codex:$normalizedPath:thread-1",
  )
}
