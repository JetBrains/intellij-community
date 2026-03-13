// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.actions.AgentSessionsCopyThreadIdFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabArchiveThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadPopupGroup
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadQuickAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsSelectThreadInToolWindowAction
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.ui.providerIcon
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
    var entryPoint: AgentWorkbenchEntryPoint? = null
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
      createNewSession = { path, provider, mode, project, capturedEntryPoint ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
        entryPoint = capturedEntryPoint
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
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK)
  }

  @Test
  fun editorTabQuickNewThreadUsesYoloModeWhenLastUsedLaunchModeIsYolo() {
    val context = editorContext(path = "/tmp/editor-project")
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
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
      lastUsedProvider = { AgentSessionProvider.CODEX },
      lastUsedLaunchMode = { AgentSessionLaunchMode.YOLO },
      createNewSession = { path, provider, mode, project, capturedEntryPoint ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
        entryPoint = capturedEntryPoint
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isInstanceOf(BadgeIcon::class.java)

    action.actionPerformed(event)

    assertThat(launchedPath).isEqualTo(context.path)
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.YOLO)
    assertThat(launchedProjectName).isEqualTo(context.project.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK)
  }

  @Test
  fun editorTabQuickNewThreadFallsBackToFirstStandardProviderWhenLastUsedIsNotEligible() {
    val context = editorContext()
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
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
      createNewSession = { _, provider, mode, _, capturedEntryPoint ->
        launchedProvider = provider
        launchedMode = mode
        entryPoint = capturedEntryPoint
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.icon).isEqualTo(providerIcon(fallbackProvider))

    action.actionPerformed(event)

    assertThat(launchedProvider).isEqualTo(fallbackProvider)
    assertThat(launchedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_QUICK)
  }

  @Test
  fun editorTabPopupNewThreadBuildsMenuAndLaunchesSelectedMode() {
    val context = editorContext(path = "/tmp/editor-project")
    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedMode: AgentSessionLaunchMode? = null
    var launchedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
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
      createNewSession = { path, provider, mode, project, capturedEntryPoint ->
        launchedPath = path
        launchedProvider = provider
        launchedMode = mode
        launchedProjectName = project.name
        entryPoint = capturedEntryPoint
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
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP)
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
      archiveThreads = { _, _, _ -> },
    )
    val unsupportedEvent = TestActionEvent.createTestEvent(unsupported)
    unsupported.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isVisible).isTrue()
    assertThat(unsupportedEvent.presentation.isEnabled).isFalse()

    val supported = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { _, _, _ -> },
    )
    val supportedEvent = TestActionEvent.createTestEvent(supported)
    supported.update(supportedEvent)
    assertThat(supportedEvent.presentation.isVisible).isTrue()
    assertThat(supportedEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun archiveThreadActionUsesThreadTargetAndPreferredLabelFromEditorTabContext() {
    val context = editorContext(threadTitle = "Refactor session setup")
    val contextTarget = context.sessionActionTarget as SessionActionTarget.Thread
    var archivedTargets: List<ArchiveThreadTarget>? = null
    var preferredSingleArchivedLabel: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null

    val action = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { targets, label, capturedEntryPoint ->
        archivedTargets = targets
        preferredSingleArchivedLabel = label
        entryPoint = capturedEntryPoint
      },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    assertThat(checkNotNull(archivedTargets)).containsExactly(
      ArchiveThreadTarget.Thread(
        path = context.path,
        provider = contextTarget.provider,
        threadId = contextTarget.threadId,
      )
    )
    assertThat(preferredSingleArchivedLabel).isEqualTo("Refactor session setup")
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP)
  }

  @Test
  fun archiveThreadActionUsesSubAgentTargetAndPreferredLabelWhenContextCarriesSubAgentId() {
    val context = editorContext(
      threadIdentity = "codex:thread-1",
      sessionId = "thread-1",
      threadId = "sub-agent-1",
      subAgentId = "sub-agent-1",
      threadTitle = "Sub-agent label",
    )
    val contextTarget = context.sessionActionTarget as SessionActionTarget.SubAgent
    var archivedTargets: List<ArchiveThreadTarget>? = null
    var preferredSingleArchivedLabel: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null

    val action = AgentSessionsEditorTabArchiveThreadAction(
      resolveContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { targets, label, capturedEntryPoint ->
        archivedTargets = targets
        preferredSingleArchivedLabel = label
        entryPoint = capturedEntryPoint
      },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    val archivedTarget = checkNotNull(archivedTargets).single()
    assertThat(archivedTarget).isEqualTo(
      ArchiveThreadTarget.SubAgent(
        path = context.path,
        provider = contextTarget.provider,
        parentThreadId = contextTarget.parentThreadId,
        subAgentId = contextTarget.subAgentId,
      )
    )
    assertThat(preferredSingleArchivedLabel).isEqualTo("Sub-agent label")
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP)
  }

  @Test
  fun selectInAgentThreadsActionEnsuresVisibilityAndActivatesToolWindow() {
    val context = editorContext(
      threadIdentity = "codex:thread-1",
      sessionId = "thread-1",
      threadId = "sub-agent-1",
      subAgentId = "sub-agent-1",
    )
    val contextTarget = context.sessionActionTarget as SessionActionTarget.Conversation
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
    assertThat(ensuredProvider).isEqualTo(contextTarget.provider)
    assertThat(ensuredThreadId).isEqualTo(contextTarget.threadId)
    assertThat(activatedProjectName).isEqualTo(context.project.name)
  }

  @Test
  fun goToSourceProjectActionOpensSourceProjectInDedicatedFrame() {
    val context = editorContext(threadId = "thread-42", sessionId = "thread-42")
    var openedPath: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null

    val action = AgentSessionsGoToSourceProjectFromEditorTabAction(
      resolveContext = { context },
      isDedicatedProject = { true },
      openProject = { path, capturedEntryPoint ->
        openedPath = path
        entryPoint = capturedEntryPoint
      },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()

    action.actionPerformed(event)
    assertThat(openedPath).isEqualTo(context.path)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP)
  }

  @Test
  fun goToSourceProjectActionHiddenOutsideDedicatedFrame() {
    val action = AgentSessionsGoToSourceProjectFromEditorTabAction(
      resolveContext = { editorContext(threadId = "thread-42", sessionId = "thread-42") },
      isDedicatedProject = { false },
      openProject = { _, _ -> error("should not open project when not in dedicated frame") },
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
  threadTitle: String = "Thread title",
  provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
  sessionId: String = "thread-1",
  isPendingThread: Boolean = false,
  subAgentId: String? = null,
): AgentChatEditorTabActionContext {
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  val threadCoordinates = provider
    ?.takeIf { sessionId.isNotBlank() }
    ?.let { resolvedProvider ->
      AgentChatThreadCoordinates(
        provider = resolvedProvider,
        sessionId = sessionId,
        isPending = isPendingThread,
      )
    }
  val normalizedSubAgentId = subAgentId?.takeIf { it.isNotBlank() }
  val effectiveThreadId = threadId.takeIf { it.isNotBlank() } ?: normalizedSubAgentId ?: sessionId
  val sessionActionTarget = when {
    threadCoordinates == null || threadCoordinates.isPending || effectiveThreadId.isBlank() -> null
    normalizedSubAgentId != null && effectiveThreadId != normalizedSubAgentId -> null
    normalizedSubAgentId != null -> {
      SessionActionTarget.SubAgent(
        path = normalizedPath,
        provider = threadCoordinates.provider,
        parentThreadId = threadCoordinates.sessionId,
        subAgentId = normalizedSubAgentId,
        title = threadTitle,
      )
    }

    else -> {
      SessionActionTarget.Thread(
        path = normalizedPath,
        provider = threadCoordinates.provider,
        threadId = effectiveThreadId,
        title = threadTitle,
      )
    }
  }
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizedPath,
    tabKey = tabKey,
    threadIdentity = threadIdentity,
    threadCoordinates = threadCoordinates,
    sessionActionTarget = sessionActionTarget,
  )
}
