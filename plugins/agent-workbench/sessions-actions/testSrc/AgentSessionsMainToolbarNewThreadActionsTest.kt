// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.ui.AgentWorkbenchActionIds
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.core.dataContextOrNull
import com.intellij.agent.workbench.sessions.actions.AgentSessionsDirectPathNewThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsNewThreadContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsNewThreadTarget
import com.intellij.agent.workbench.sessions.actions.AgentSessionsMainToolbarNewThreadAction
import com.intellij.agent.workbench.sessions.actions.ProfileQuickStartAction
import com.intellij.agent.workbench.sessions.actions.buildNewThreadInitialMessageRequest
import com.intellij.agent.workbench.sessions.actions.resolveAgentSessionsMainToolbarNewThreadContext
import com.intellij.agent.workbench.sessions.actions.resolveQuickStartProjectPopupAnchor
import com.intellij.agent.workbench.sessions.actions.shouldOpenInlineNewThreadPrompt
import com.intellij.platform.ai.agent.sessions.core.providers.builtInLaunchProfileId
import com.intellij.platform.ai.agent.sessions.core.providers.initialMessageRequestForLaunchProfile
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.ui.AgentWorkbenchPopupStep
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.BadgeIcon
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

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
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.YOLO) },
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
  fun updateUsesEffectiveDefaultProviderIconWhenNoDefaultProfile() {
    val context = newThreadContext()
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isEqualTo(codexBridge.icon)
    assertThat(event.presentation.text).isEqualTo(AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.NewThreadQuick.text",
      AgentSessionsBundle.message(codexBridge.quickStartLabelKey),
    ))
    assertThat(event.presentation.description)
      .contains(AgentSessionsBundle.message(codexBridge.quickStartLabelKey))
  }

  @Test
  fun updateDoesNotProbeProviderCliAvailability() {
    val context = newThreadContext()
    var cliChecks = 0
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      onCliAvailable = { cliChecks++ },
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action)
    context.project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      mapOf(AgentSessionProvider.from("codex") to true),
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
      provider = AgentSessionProvider.from("terminal"),
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
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("terminal"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewTerminalSessionQuick.text"))
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message(
        "action.AgentWorkbenchSessions.MainToolbar.NewThread.profile.description",
        "Terminal Session",
        "Terminal Session",
        "toolbar-project",
      ))
  }

  @Test
  fun updateSessionUsesStableQuickStartAction() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
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
  fun getMainActionReturnsQuickStartForDirectTargetWithDefaultProfile() {
    val path = "/tmp/toolbar-project"
    val context = newThreadContext(path = path)
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var launchedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val activeProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.YOLO)
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { capturedPath, profile, project, capturedEntryPoint ->
        launchedPath = capturedPath
        launchedProfile = profile
        launchedProjectName = project.name
        entryPoint = capturedEntryPoint
      },
      defaultLaunchProfileId = { activeProfileId },
    )
    val event = TestActionEvent.createTestEvent(action)

    val mainAction = action.getMainAction(event)

    assertThat(mainAction).isInstanceOf(ProfileQuickStartAction::class.java)
    val quickAction = mainAction as ProfileQuickStartAction
    assertThat(action.getMainAction(event)).isSameAs(quickAction)
    quickAction.actionPerformed(TestActionEvent.createTestEvent(quickAction))

    assertThat(launchedPath).isEqualTo(normalizeAgentWorkbenchPath(path))
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProjectName).isEqualTo(context.project.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TOOLBAR)
    assertThat(activeProfileId).isEqualTo(builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.YOLO))
  }

  @Test
  fun mainToolbarQuickStartPassesActionDataContextToInvocationData() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, context.project)
      .build()
    var capturedInvocationData: AgentPromptInvocationData? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSessionWithInvocationData = { _, _, _, _, invocationData ->
        capturedInvocationData = invocationData
      },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val mainAction = checkNotNull(action.getMainAction(TestActionEvent.createTestEvent(action, dataContext)))

    mainAction.actionPerformed(TestActionEvent.createTestEvent(mainAction, dataContext))

    assertThat(capturedInvocationData?.actionId).isEqualTo(AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD)
    assertThat(capturedInvocationData?.dataContextOrNull()).isSameAs(dataContext)
  }

  @Test
  fun getMainActionLaunchesExplicitDefaultUserProfile() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    val defaultProfile = AgentPromptLaunchProfile(
      id = "user:careful-pi",
      name = "Careful Pi",
      providerId = AgentSessionProvider.from("pi").value,
      generationSettings = AgentPromptGenerationSettings(
        modelId = "pi:model-1",
        reasoningEffort = AgentPromptReasoningEffort.HIGH,
      ),
    )
    var launchedProfile: AgentPromptLaunchProfile? = null
    val piBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("pi"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLabelKeyOverride = "toolwindow.action.new.session.pi",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(piBridge) },
      createNewSession = { _, profile, _, _ ->
        launchedProfile = profile
      },
      userLaunchProfiles = { listOf(defaultProfile) },
      defaultLaunchProfileId = { defaultProfile.id },
    )
    val mainAction = checkNotNull(action.getMainAction(TestActionEvent.createTestEvent(action)))

    mainAction.actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(launchedProfile).isEqualTo(defaultProfile)
  }

  @Test
  fun getMainActionUsesEffectiveDefaultProviderWhenNoDefaultProfile() {
    val context = newThreadContext()
    var launchedProfile: AgentPromptLaunchProfile? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, profile, _, _ ->
        launchedProfile = profile
      },
    )
    val mainAction = checkNotNull(action.getMainAction(TestActionEvent.createTestEvent(action)))

    mainAction.actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
  }

  @Test
  fun directPathPrimaryActionUsesQuickStartEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var beforeActionCount = 0
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, profile, _, capturedEntryPoint ->
        launchedPath = path
        launchedProfile = profile
        entryPoint = capturedEntryPoint
      },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      beforeAction = { beforeActionCount++ },
    )
    val mainAction = checkNotNull(action.getMainAction(TestActionEvent.createTestEvent(action)))

    mainAction.actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(beforeActionCount).isEqualTo(1)
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY)
  }

  @Test
  fun directPathPopupChildrenUsePopupEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var beforeActionCount = 0
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, profile, _, capturedEntryPoint ->
        launchedPath = path
        launchedProfile = profile
        entryPoint = capturedEntryPoint
      },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      beforeAction = { beforeActionCount++ },
    )
    val event = TestActionEvent.createTestEvent(action)
    val childAction = action.actionGroup.getChildren(event).first { child -> child !is Separator }

    childAction.actionPerformed(TestActionEvent.createTestEvent(childAction))

    assertThat(beforeActionCount).isEqualTo(1)
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_POPUP)
  }

  @Test
  fun directPathPopupChildDispatchesWhenTransientSplitButtonIsHidden() {
    val project = ProjectManager.getInstance().defaultProject
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { "/work/project-a" },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, profile, _, capturedEntryPoint ->
        launchedPath = path
        launchedProfile = profile
        entryPoint = capturedEntryPoint
      },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )

    val result = timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val component = action.createCustomComponent(Presentation(), ActionPlaces.TOOLWINDOW_CONTENT)
        val childAction = action.actionGroup.getChildren(TestActionEvent.createTestEvent(action))
          .first { child -> child !is Separator }
        val dataContext = SimpleDataContext.builder()
          .add(CommonDataKeys.PROJECT, project)
          .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
          .build()

        assertThat(component.isShowing).isFalse()
        ActionUtil.performAction(childAction, TestActionEvent.createTestEvent(childAction, dataContext))
      }
    }

    assertThat(result.isPerformed).isTrue()
    assertThat(launchedPath).isEqualTo("/work/project-a")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
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
          provider = AgentSessionProvider.from("codex"),
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
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> launched = true },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      showPicker = { _, _ -> pickerShown++ },
    )
    val mainAction = action.getMainAction(TestActionEvent.createTestEvent(action))

    assertThat(mainAction).isInstanceOf(ProfileQuickStartAction::class.java)
    checkNotNull(mainAction).actionPerformed(TestActionEvent.createTestEvent(mainAction))
    assertThat(launched).isFalse()
    assertThat(pickerShown).isEqualTo(1)
  }

  @Test
  fun primaryClickUsesCachedProviderAvailabilityBeforeQuickLaunchAndFallsBackWhenDefaultUnavailable() {
    val context = newThreadContext(path = "/tmp/toolbar-project")
    var cliChecks = 0
    var launchedProfile: AgentPromptLaunchProfile? = null
    var pickerShown = 0
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      onCliAvailable = { cliChecks++ },
    )
    val claudeBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { _, profile, _, _ -> launchedProfile = profile },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      showPicker = { _, _ -> pickerShown++ },
    )
    context.project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(
      mapOf(
        AgentSessionProvider.from("codex") to false,
        AgentSessionProvider.from("claude") to true,
      ),
    )
    val mainAction = action.getMainAction(TestActionEvent.createTestEvent(action))

    assertThat(mainAction).isInstanceOf(ProfileQuickStartAction::class.java)
    checkNotNull(mainAction).actionPerformed(TestActionEvent.createTestEvent(mainAction))

    assertThat(cliChecks).isZero()
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("claude").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(pickerShown).isZero()
  }

  @Test
  fun mainToolbarPickerShowsBuiltInAndUserLaunchProfiles() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val defaultProfileId = "user:careful"
    var launchedPath: String? = null
    var launchedProfile: AgentPromptLaunchProfile? = null
    val providerIcon = EmptyIcon.create(17)
    val customProfile = AgentPromptLaunchProfile(
      id = "user:careful",
      name = "Careful Codex",
      providerId = AgentSessionProvider.from("codex").value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      iconOverride = providerIcon,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { path, profile, _, _ ->
        launchedPath = path
        launchedProfile = profile
      },
      userLaunchProfiles = { listOf(customProfile) },
      defaultLaunchProfileId = { defaultProfileId },
    )
    val event = TestActionEvent.createTestEvent(action)

    val children = action.actionGroup.getChildren(event)

    assertThat(children.filterIsInstance<Separator>()).hasSize(3)
    assertThat(children.filterNot { child -> child is Separator }.map { child -> child.templatePresentation.text }).containsExactly(
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
      "Careful Codex",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
      MANAGE_LAUNCH_PROFILES_TEXT,
    )
    val selectedAction = children.single { child -> child.templatePresentation.text == "Careful Codex" }
    val unselectedAction =
      children.single { child -> child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex") }
    val selectedPopupEvent = popupEvent(selectedAction)
    val unselectedPopupEvent = popupEvent(unselectedAction)

    assertThat(selectedAction.templatePresentation.icon).isSameAs(providerIcon)
    assertThat(selectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
    assertThat(unselectedAction.templatePresentation.icon).isSameAs(providerIcon)
    assertThat(unselectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()

    selectedAction.update(selectedPopupEvent)
    unselectedAction.update(unselectedPopupEvent)

    // The default profile is marked only by the trailing checkmark (SECONDARY_ICON); it must not be a
    // selected Toggleable, otherwise the platform action menu paints a PoppedIcon background behind the icon.
    assertThat(selectedAction).isNotInstanceOf(Toggleable::class.java)
    assertThat(Toggleable.isSelected(selectedPopupEvent.presentation)).isFalse()
    assertThat(selectedPopupEvent.presentation.icon).isSameAs(providerIcon)
    assertThat(selectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
    assertThat(Toggleable.isSelected(unselectedPopupEvent.presentation)).isFalse()
    assertThat(unselectedPopupEvent.presentation.icon).isSameAs(providerIcon)
    assertThat(unselectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()

    children.single { child -> child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex") }
      .actionPerformed(TestActionEvent.createTestEvent())

    assertThat(defaultProfileId).isEqualTo(customProfile.id)
    assertThat(launchedPath).isEqualTo("/tmp/repo-direct")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(launchedProfile?.kind).isEqualTo(AgentPromptLaunchProfileKind.BUILT_IN)
  }

  @Test
  fun mainToolbarPickerRowPassesActionDataContextToInvocationData() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, context.project)
      .build()
    var capturedInvocationData: AgentPromptInvocationData? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSessionWithInvocationData = { _, _, _, _, invocationData ->
        capturedInvocationData = invocationData
      },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action, dataContext)
    val row = action.createProfilePickerRowsForTest(event).single { popupRow ->
      popupRow.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex")
    }

    checkNotNull(row.onChosen).invoke()

    assertThat(capturedInvocationData?.actionId).isEqualTo(AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD)
    assertThat(capturedInvocationData?.dataContextOrNull()).isSameAs(dataContext)
  }

  @Test
  fun mainToolbarPickerSelectionDoesNotChangeDefaultProfileMarking() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val defaultProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
    var launchedProfile: AgentPromptLaunchProfile? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, profile, _, _ -> launchedProfile = profile },
      defaultLaunchProfileId = { defaultProfileId },
    )
    val event = TestActionEvent.createTestEvent(action)
    val children = action.actionGroup.getChildren(event)
    val yoloAction = children.single { child ->
      child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }

    yoloAction.actionPerformed(TestActionEvent.createTestEvent(yoloAction))

    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    val rows = action.createProfilePickerRowsForTest(event)
    val defaultRow = rows.single { row -> row.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex") }
    val chosenRow = rows.single { row -> row.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo") }
    assertThat(defaultRow.selected).isTrue()
    assertThat(defaultRow.secondaryIcon).isNotNull()
    assertThat(chosenRow.selected).isFalse()
    assertThat(chosenRow.secondaryIcon).isNull()
  }

  @Test
  fun mainToolbarPickerIncludesManageLaunchProfilesActionWhenRegistered() {
    var managePerformed = false
    val cleanup = registerManageLaunchProfilesAction { managePerformed = true }
    try {
      val context = newThreadContext(path = "/tmp/repo-direct")
      var launchedProfile: AgentPromptLaunchProfile? = null
      val codexBridge = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("codex"),
        supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
        cliAvailable = true,
      )
      val action = AgentSessionsMainToolbarNewThreadAction(
        resolveContext = { context },
        allBridges = { listOf(codexBridge) },
        createNewSession = { _, profile, _, _ -> launchedProfile = profile },
        defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      )

      val children = action.actionGroup.getChildren(TestActionEvent.createTestEvent(action))

      assertThat(children.filterIsInstance<Separator>()).hasSize(1)
      assertThat(children.filterNot { child -> child is Separator }.map { child -> child.templatePresentation.text }).containsExactly(
        AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
        MANAGE_LAUNCH_PROFILES_TEXT,
      )

      children.single { child -> child.templatePresentation.text == MANAGE_LAUNCH_PROFILES_TEXT }
        .actionPerformed(TestActionEvent.createTestEvent())

      assertThat(managePerformed).isTrue()
      assertThat(launchedProfile).isNull()
    }
    finally {
      cleanup()
    }
  }

  @Test
  fun mainToolbarProfileRowsUseSharedListPopupIconsAndDefaultCheckmark() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val activeProfileId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { activeProfileId },
    )
    val rows = action.createProfilePickerRowsForTest(TestActionEvent.createTestEvent(action))
    val popupStep = AgentWorkbenchPopupStep(rows)

    val selectedRow = rows.single { row -> row.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex") }
    val yoloRow = rows.single { row -> row.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo") }
    assertThat(selectedRow.selected).isTrue()
    assertThat(selectedRow.primaryIcon).isNotNull()
    assertThat(selectedRow.secondaryIcon).isNotNull()
    assertThat(yoloRow.selected).isFalse()
    assertThat(yoloRow.secondaryIcon).isNull()
    assertThat(popupStep.getIconFor(selectedRow)).isNull()
    assertThat(popupStep.getSelectedIconFor(selectedRow)).isNull()
    assertThat(popupStep.getSecondaryIconFor(selectedRow)).isNull()
  }

  @Test
  fun mainToolbarPickerShowsSingleTopLevelManageLaunchProfilesActionForCandidates() {
    val cleanup = registerManageLaunchProfilesAction()
    try {
      val context = newThreadContext(
        projectPathCandidates = listOf(
          projectCandidate(path = "/work/repo-a", displayName = "Project A"),
          projectCandidate(path = "/tmp/repo-a", displayName = "/tmp/repo-a"),
        ),
      )
      val codexBridge = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("codex"),
        supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
        cliAvailable = true,
      )
      val action = AgentSessionsMainToolbarNewThreadAction(
        resolveContext = { context },
        allBridges = { listOf(codexBridge) },
        createNewSession = { _, _, _, _ -> },
        defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
      )
      val event = TestActionEvent.createTestEvent(action)

      val children = action.actionGroup.getChildren(event)

      assertThat(children.filterIsInstance<Separator>()).hasSize(1)
      assertThat(children.filterNot { child -> child is Separator }.map { child -> child.templatePresentation.text }).containsExactly(
        "Project A",
        "/tmp/repo-a",
        MANAGE_LAUNCH_PROFILES_TEXT,
      )
      val firstProjectActions = (children.first() as ActionGroup).getChildren(event)
      assertThat(firstProjectActions.filterNot { child -> child is Separator }
                   .map { child -> child.templatePresentation.text }).containsExactly(
        AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
      )
    }
    finally {
      cleanup()
    }
  }

  @Test
  fun mainToolbarPickerUsesBuiltInOverrideWithoutDuplicatingProfile() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val overriddenBuiltInId = builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD)
    val overriddenProfile = AgentPromptLaunchProfile(
      id = overriddenBuiltInId,
      name = "Careful Codex",
      kind = AgentPromptLaunchProfileKind.USER,
      providerId = AgentSessionProvider.from("codex").value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      userLaunchProfiles = { listOf(overriddenProfile) },
      defaultLaunchProfileId = { overriddenBuiltInId },
    )
    val event = TestActionEvent.createTestEvent(action)

    val children = action.actionGroup.getChildren(event)

    assertThat(children.filterNot { child -> child is Separator }.map { child -> child.templatePresentation.text }).containsExactly(
      "Careful Codex",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
      MANAGE_LAUNCH_PROFILES_TEXT,
    )
  }

  @Test
  fun mainToolbarPickerMarksEffectiveDefaultWhenNoDefaultProfileStored() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val providerIcon = EmptyIcon.create(17)
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      iconOverride = providerIcon,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { null },
    )
    val event = TestActionEvent.createTestEvent(action)

    val children = action.actionGroup.getChildren(event)
    val selectedAction = children.single { child ->
      child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex")
    }
    val selectedPopupEvent = popupEvent(selectedAction)

    assertThat(selectedAction.templatePresentation.icon).isSameAs(providerIcon)
    assertThat(selectedAction.templatePresentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()

    selectedAction.update(selectedPopupEvent)

    assertThat(selectedAction).isNotInstanceOf(Toggleable::class.java)
    assertThat(Toggleable.isSelected(selectedPopupEvent.presentation)).isFalse()
    assertThat(selectedPopupEvent.presentation.icon).isSameAs(providerIcon)
    assertThat(selectedPopupEvent.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNotNull()
  }

  @Test
  fun launchProfileInitialMessageRequestDoesNotStorePlanMode() {
    val profile = AgentPromptLaunchProfile(
      id = "user:plan",
      name = "Plan Codex",
      providerId = AgentSessionProvider.from("codex").value,
    )

    val request = initialMessageRequestForLaunchProfile(profile)

    assertThat(request.prompt).isEmpty()
    assertThat(request.providerOptionIds).isEmpty()
  }

  @Test
  fun nonInlineNewThreadInitialMessageRequestIncludesCollectedInvocationContext() {
    val profile = AgentPromptLaunchProfile(
      id = "user:context",
      name = "Context Codex",
      providerId = AgentSessionProvider.from("codex").value,
    )
    val invocationData = invocationData()
    var collectedInvocationData: AgentPromptInvocationData? = null

    val request = buildNewThreadInitialMessageRequest(
      profile = profile,
      projectPath = "/work/repo",
      invocationData = invocationData,
      collectDefaultContext = { data ->
        collectedInvocationData = data
        listOf(contextItem(body = "  selected code  "))
      },
    )

    assertThat(collectedInvocationData).isSameAs(invocationData)
    assertThat(request.prompt).isEmpty()
    assertThat(request.projectPath).isEqualTo("/work/repo")
    assertThat(request.contextItems.single().body).isEqualTo("selected code")
    assertThat(request.contextEnvelopeSummary?.softCapExceeded).isFalse()
    assertThat(request.contextEnvelopeSummary?.autoTrimApplied).isFalse()
  }

  @Test
  fun nonInlineNewThreadInitialMessageRequestAutoTrimsOversizedInvocationContext() {
    val profile = AgentPromptLaunchProfile(
      id = "user:large-context",
      name = "Large Context Codex",
      providerId = AgentSessionProvider.from("codex").value,
    )
    val oversizedBody = "x".repeat(AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS * 2)

    val request = buildNewThreadInitialMessageRequest(
      profile = profile,
      projectPath = "/work/repo",
      invocationData = invocationData(),
      collectDefaultContext = { listOf(contextItem(body = oversizedBody)) },
    )
    val summary = checkNotNull(request.contextEnvelopeSummary)

    assertThat(summary.softCapExceeded).isTrue()
    assertThat(summary.autoTrimApplied).isTrue()
    assertThat(request.contextItems).isNotEmpty()
    assertThat(request.contextItems.joinToString("\n") { item -> item.body }).isNotEqualTo(oversizedBody)
    assertThat(AgentPromptContextEnvelopeFormatter.measureContextBlockChars(
      items = request.contextItems,
      summary = summary,
      projectPath = request.projectPath,
    )).isLessThanOrEqualTo(AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS)
  }

  @Test
  fun inlineNewThreadPromptRoutingRequiresRegistryAndPromptCapableProvider() {
    val promptProvider = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsPromptLaunch = true,
    )
    val terminalProvider = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("terminal"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsPromptLaunch = false,
    )

    assertThat(shouldOpenInlineNewThreadPrompt(registryEnabled = true, descriptor = promptProvider)).isTrue()
    assertThat(shouldOpenInlineNewThreadPrompt(registryEnabled = false, descriptor = promptProvider)).isFalse()
    assertThat(shouldOpenInlineNewThreadPrompt(registryEnabled = true, descriptor = terminalProvider)).isFalse()
    assertThat(shouldOpenInlineNewThreadPrompt(registryEnabled = true, descriptor = null)).isFalse()
  }

  @Test
  fun pickerGroupReturnsFlatProviderMenuForDirectTarget() {
    val context = newThreadContext(path = "/tmp/repo-direct")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { _, _, _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    val children = action.actionGroup.getChildren(event)

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
    var launchedProfile: AgentPromptLaunchProfile? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { capturedPath, profile, _, capturedEntryPoint ->
        launchedPath = capturedPath
        launchedProfile = profile
        entryPoint = capturedEntryPoint
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    val children = action.actionGroup.getChildren(event)

    val projectGroups = children.filterIsInstance<ActionGroup>()
    assertThat(projectGroups.map { it.templatePresentation.text }).containsExactly("Project A", "/tmp/repo-a")
    val secondProjectGroup = projectGroups.last()
    val secondProjectChildren = secondProjectGroup.getChildren(event)
    val yoloAction = secondProjectChildren.first { child ->
      child !is Separator && child.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }
    yoloAction.actionPerformed(TestActionEvent.createTestEvent(yoloAction))

    assertThat(launchedPath).isEqualTo("/tmp/repo-a")
    assertThat(launchedProfile?.providerId).isEqualTo(AgentSessionProvider.from("codex").value)
    assertThat(launchedProfile?.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.TOOLBAR)
  }

  @Test
  fun updateHidesActionWhenNoProvidersAreRegistered() {
    val context = newThreadContext()
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { emptyList() },
      createNewSession = { _, _, _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun updateHidesActionWhenContextIsUnavailable() {
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { null },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun tooltipNamesProviderAndProjectForDirectTarget() {
    val context = newThreadContext(path = "/work/my-repo")
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    val expected = AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.MainToolbar.NewThread.profile.description",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex"),
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
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
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
    val context = AgentSessionsNewThreadContext(
      project = ProjectManager.getInstance().defaultProject,
      resolveTarget = {
        candidatesResolved = true
        AgentSessionsNewThreadTarget.Candidates(
          listOf(projectCandidate(path = "/work/repo-a", displayName = "Project A")),
        )
      },
      resolveTargetForUpdate = { null },
    )
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsMainToolbarNewThreadAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge) },
      createNewSession = { _, _, _, _ -> },
      defaultLaunchProfileId = { builtInLaunchProfileId(AgentSessionProvider.from("codex"), AgentSessionLaunchMode.STANDARD) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(candidatesResolved).isFalse()
    assertThat(event.presentation.description).contains(
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.target.choose"),
    )
  }

  @Test
  fun quickNewThreadPopupAnchorPrefersInputEventComponent() {
    val action = AgentSessionsMainToolbarNewThreadAction(resolveContext = { null }, allBridges = { emptyList() })
    val inputComponent = JPanel()
    val contextComponent = JPanel()
    val event = AnActionEvent(
      { dataId -> if (dataId == PlatformCoreDataKeys.CONTEXT_COMPONENT.name) contextComponent else null },
      action.templatePresentation.clone(),
      "",
      ActionUiKind.NONE,
      MouseEvent(inputComponent, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false),
      0,
      ActionManager.getInstance(),
    )

    assertThat(resolveQuickStartProjectPopupAnchor(event)).isSameAs(inputComponent)
  }

  @Test
  fun quickNewThreadPopupAnchorFallsBackToContextComponent() {
    val action = AgentSessionsMainToolbarNewThreadAction(resolveContext = { null }, allBridges = { emptyList() })
    val contextComponent = JPanel()
    val event = AnActionEvent(
      { dataId -> if (dataId == PlatformCoreDataKeys.CONTEXT_COMPONENT.name) contextComponent else null },
      action.templatePresentation.clone(),
      "",
      ActionUiKind.NONE,
      null,
      0,
      ActionManager.getInstance(),
    )

    assertThat(resolveQuickStartProjectPopupAnchor(event)).isSameAs(contextComponent)
  }

  @Test
  fun quickNewThreadPopupAnchorReturnsNullWithoutUiComponent() {
    val action = AgentSessionsMainToolbarNewThreadAction(resolveContext = { null }, allBridges = { emptyList() })
    val event = TestActionEvent.createTestEvent(action)

    assertThat(resolveQuickStartProjectPopupAnchor(event)).isNull()
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

    val target = checkNotNull(context).target as AgentSessionsNewThreadTarget.Direct
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

    val target = checkNotNull(context).target as AgentSessionsNewThreadTarget.Direct
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

    val target = checkNotNull(context).target as AgentSessionsNewThreadTarget.Direct
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

    val candidates = (checkNotNull(context).target as AgentSessionsNewThreadTarget.Candidates).candidates
    assertThat(openProjectPathsResolved).isTrue()
    assertThat(candidates.map(AgentPromptProjectPathCandidate::path))
      .containsExactly("/work/repo-a", "/tmp/repo-a")
  }

  private fun popupEvent(action: AnAction): AnActionEvent {
    return AnActionEvent.createEvent(
      action,
      DataContext.EMPTY_CONTEXT,
      null,
      "",
      ActionUiKind.POPUP,
      null,
    )
  }

  private fun registerManageLaunchProfilesAction(onPerformed: () -> Unit = {}): () -> Unit {
    val actionManager = ActionManager.getInstance()
    val actionId = AgentWorkbenchActionIds.Prompt.MANAGE_LAUNCH_PROFILES
    val previousAction = actionManager.getAction(actionId)
    if (previousAction != null) {
      actionManager.unregisterAction(actionId)
    }
    val action = object : AnAction(MANAGE_LAUNCH_PROFILES_TEXT) {
      override fun actionPerformed(e: AnActionEvent) {
        onPerformed()
      }
    }
    actionManager.registerAction(actionId, action)
    return {
      actionManager.unregisterAction(actionId)
      if (previousAction != null) {
        actionManager.registerAction(actionId, previousAction)
      }
    }
  }
}

private const val MANAGE_LAUNCH_PROFILES_TEXT: String = "Manage Launch Profiles…"

private fun editorContext(): AgentChatEditorTabActionContext {
  val path = "/work/event-chat-repo"
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizedPath,
    tabKey = "codex:$normalizedPath:thread-1",
  )
}

private fun invocationData(): AgentPromptInvocationData {
  val project = ProjectManager.getInstance().defaultProject
  return AgentPromptInvocationData(
    project = project,
    actionId = AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD,
    actionText = "New Thread",
    actionPlace = ActionPlaces.MAIN_TOOLBAR,
    invokedAtMs = 0,
  )
}

private fun contextItem(body: String): AgentPromptContextItem {
  return AgentPromptContextItem(
    rendererId = AgentPromptContextRendererIds.SNIPPET,
    title = "Selection",
    body = body,
    source = "test",
  )
}
