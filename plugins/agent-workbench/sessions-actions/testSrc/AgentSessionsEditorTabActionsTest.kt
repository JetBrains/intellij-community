// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.actions.AgentSessionsCopyThreadIdFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabArchiveThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadPopupGroup
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadQuickAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsSelectThreadInToolWindowAction
import com.intellij.agent.workbench.sessions.actions.providerIcon
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsEditorTabActionsTest {
  @Test
  fun editorTabQuickNewThreadUsesLastUsedProviderAndLaunchesStandardSession() {
    val context = editorContext(path = "/tmp/editor-project")
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProjectName: String? = null
    val codexBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsEditorTabNewThreadQuickAction(
      resolveContext = { context },
      allBridges = { listOf(codexBridge, claudeBridge) },
      lastUsedProvider = { AgentSessionProvider.CLAUDE },
      createNewSession = { path, provider, mode, project ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isEqualTo(providerIcon(AgentSessionProvider.CLAUDE))

    action.actionPerformed(event)

    assertThat(launchedPath).isEqualTo(context.path)
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(launchedProjectName).isEqualTo(context.project.name)
  }

  @Test
  fun editorTabQuickNewThreadFallsBackToFirstStandardProviderWhenLastUsedIsNotEligible() {
    val context = editorContext()
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    val fallbackProvider = AgentSessionProvider.from("fallback")
    val codexYoloOnlyBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val fallbackBridge = TestAgentSessionProviderBridge(
      provider = fallbackProvider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val action = AgentSessionsEditorTabNewThreadQuickAction(
      resolveContext = { context },
      allBridges = { listOf(codexYoloOnlyBridge, fallbackBridge) },
      lastUsedProvider = { AgentSessionProvider.CODEX },
      createNewSession = { _, provider, mode, _ ->
        launchedProvider = provider
        launchedMode = mode
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isEqualTo(providerIcon(fallbackProvider))

    action.actionPerformed(event)

    assertThat(launchedProvider).isEqualTo(fallbackProvider)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
  }

  @Test
  fun editorTabPopupNewThreadBuildsMenuAndLaunchesSelectedMode() {
    val context = editorContext(path = "/tmp/editor-project")
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProjectName: String? = null
    val codexBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )
    val claudeBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val group = AgentSessionsEditorTabNewThreadPopupGroup(
      resolveContext = { context },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { path, provider, mode, project ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
      },
    )
    val event = TestActionEvent.createTestEvent(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.isPopupGroup).isTrue()
    assertThat(event.presentation.isPerformGroup).isFalse()

    val children = group.getChildren(event)
    assertThat(children).hasSize(5)

    val yoloAction = children.first { action ->
      action.templatePresentation.text == AgentSessionsBundle.message("toolwindow.action.new.session.codex.yolo")
    }
    yoloAction.actionPerformed(TestActionEvent.createTestEvent(yoloAction))

    assertThat(launchedPath).isEqualTo(context.path)
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProjectName).isEqualTo(context.project.name)
  }

  @Test
  fun editorTabPopupNewThreadHiddenWithoutEditorContext() {
    val codexBridge = TestAgentSessionProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val group = AgentSessionsEditorTabNewThreadPopupGroup(
      resolveContext = { null },
      allBridges = { listOf(codexBridge) },
    )
    val event = TestActionEvent.createTestEvent(group)

    group.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
    assertThat(group.getChildren(event)).isEmpty()
  }

  @Test
  fun archiveThreadActionVisibleAndEnabledOnlyWhenProviderSupportsArchive() {
    val context = editorContext()

    val unsupported = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { false },
      archiveThreads = { _ -> },
    )
    val unsupportedEvent = TestActionEvent.createTestEvent(unsupported)
    unsupported.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isVisible).isTrue()
    assertThat(unsupportedEvent.presentation.isEnabled).isFalse()

    val supported = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { _ -> },
    )
    val supportedEvent = TestActionEvent.createTestEvent(supported)
    supported.update(supportedEvent)
    assertThat(supportedEvent.presentation.isVisible).isTrue()
    assertThat(supportedEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun archiveThreadActionUsesSubAgentTargetWhenContextCarriesSubAgentId() {
    val context = editorContext(
      threadIdentity = "codex:thread-1",
      sessionId = "thread-1",
      threadId = "sub-agent-1",
      subAgentId = "sub-agent-1",
    )
    var archivedTargets: List<ArchiveThreadTarget>? = null

    val action = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { targets -> archivedTargets = targets },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    val archivedTarget = checkNotNull(archivedTargets).single()
    assertThat(archivedTarget).isEqualTo(
      ArchiveThreadTarget.SubAgent(
        path = context.path,
        provider = checkNotNull(context.provider),
        parentThreadId = context.sessionId,
        subAgentId = checkNotNull(context.subAgentId),
      )
    )
  }

  @Test
  fun selectInAgentThreadsActionEnsuresVisibilityAndActivatesToolWindow() {
    val context = editorContext(
      threadIdentity = "codex:thread-1",
      sessionId = "thread-1",
      threadId = "sub-agent-1",
      subAgentId = "sub-agent-1",
    )
    var ensuredPath: String? = null
    var ensuredProvider: AgentSessionProvider? = null
    var ensuredThreadId: String? = null
    var activatedProjectName: String? = null

    val action = AgentSessionsSelectThreadInToolWindowAction(
      resolveContext = { context },
      ensureThreadVisible = { path, provider, threadId ->
        ensuredPath = path
        ensuredProvider = provider
        ensuredThreadId = threadId
      },
      activateSessionsToolWindow = { project ->
        activatedProjectName = project.name
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()

    action.actionPerformed(event)
    assertThat(ensuredPath).isEqualTo(context.path)
    assertThat(ensuredProvider).isEqualTo(context.provider)
    assertThat(ensuredThreadId).isEqualTo(context.threadId)
    assertThat(activatedProjectName).isEqualTo(context.project.name)
  }

  @Test
  fun goToSourceProjectActionOpensSourceProjectInDedicatedFrame() {
    val context = editorContext(threadId = "thread-42", sessionId = "thread-42")
    var openedPath: String? = null

    val action = AgentSessionsGoToSourceProjectFromEditorTabAction(
      resolveContext = { context },
      isDedicatedProject = { true },
      openProject = { path -> openedPath = path },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()

    action.actionPerformed(event)
    assertThat(openedPath).isEqualTo(context.path)
  }

  @Test
  fun goToSourceProjectActionHiddenOutsideDedicatedFrame() {
    val action = AgentSessionsGoToSourceProjectFromEditorTabAction(
      resolveContext = { editorContext(threadId = "thread-42", sessionId = "thread-42") },
      isDedicatedProject = { false },
      openProject = { _ -> error("should not open project when not in dedicated frame") },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun copyThreadIdActionCopiesThreadId() {
    val context = editorContext(
      threadIdentity = "codex:thread-42",
      sessionId = "thread-42",
      threadId = "sub-agent-42",
    )
    var copiedThreadId: String? = null

    val action = AgentSessionsCopyThreadIdFromEditorTabAction(
      resolveContext = { context },
      copyToClipboard = { threadId -> copiedThreadId = threadId },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()

    action.actionPerformed(event)
    assertThat(copiedThreadId).isEqualTo("sub-agent-42")
  }

  @Test
  fun copyThreadIdActionDisabledWhenSessionIdBlank() {
    val context = editorContext(threadId = "thread-42", sessionId = "")
    val action = AgentSessionsCopyThreadIdFromEditorTabAction(
      resolveContext = { context },
      copyToClipboard = { _ -> error("should not copy blank thread id") },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun copyThreadIdActionDisabledForPendingIdentity() {
    val context = editorContext(
      threadIdentity = "codex:new-123",
      threadId = "thread-42",
      sessionId = "new-123",
      isPendingThread = true,
    )
    val action = AgentSessionsCopyThreadIdFromEditorTabAction(
      resolveContext = { context },
      copyToClipboard = { _ -> error("should not copy pending thread id") },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun editorTabActionsAreHiddenForNonAgentChatFiles() {
    val action = AgentSessionsSelectThreadInToolWindowAction(
      resolveContext = { null },
      ensureThreadVisible = { _, _, _ -> },
      activateSessionsToolWindow = { _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }
}

private fun editorContext(
  path: String = "/tmp/project",
  tabKey: String = "tab-pending-1",
  threadIdentity: String = "codex:thread-1",
  threadId: String = "thread-1",
  provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
  sessionId: String = "thread-1",
  isPendingThread: Boolean = false,
  subAgentId: String? = null,
): AgentChatEditorTabActionContext {
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath(path),
    tabKey = tabKey,
    threadIdentity = threadIdentity,
    threadId = threadId,
    provider = provider,
    sessionId = sessionId,
    isPendingThread = isPendingThread,
    subAgentId = subAgentId,
  )
}
