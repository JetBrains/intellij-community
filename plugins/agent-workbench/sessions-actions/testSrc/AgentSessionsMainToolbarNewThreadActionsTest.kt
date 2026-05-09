// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadTarget
import com.intellij.agent.workbench.sessions.actions.AgentSessionsMainToolbarNewThreadAction
import com.intellij.agent.workbench.sessions.actions.PickerActionGroup
import com.intellij.agent.workbench.sessions.actions.QuickStartAction
import com.intellij.agent.workbench.sessions.actions.resolveAgentSessionsMainToolbarNewThreadContext
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.BadgeIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsMainToolbarNewThreadActionsTest {
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
    assertThat(quickAction.templatePresentation.text).isEqualTo(AgentSessionsBundle.message(
      "action.AgentWorkbenchSessions.NewThreadQuick.text",
      AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo"),
    ))
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
  fun getMainActionReturnsNullForCandidatesTargetSoClickFallsThroughToPicker() {
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

    assertThat(action.getMainAction(TestActionEvent.createTestEvent(action))).isNull()
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
