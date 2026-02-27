// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsEditorTabActionsTest {
  @Test
  fun archiveThreadActionVisibleAndEnabledOnlyWhenProviderSupportsArchive() {
    val context = editorContext()

    val unsupported = AgentSessionsArchiveThreadAction(
      resolveTreeContext = { null },
      resolveEditorContext = { context },
      canArchiveProvider = { false },
      archiveThreads = { _ -> },
    )
    val unsupportedEvent = TestActionEvent.createTestEvent(unsupported)
    unsupported.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isVisible).isTrue()
    assertThat(unsupportedEvent.presentation.isEnabled).isFalse()

    val supported = AgentSessionsArchiveThreadAction(
      resolveTreeContext = { null },
      resolveEditorContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { _ -> },
    )
    val supportedEvent = TestActionEvent.createTestEvent(supported)
    supported.update(supportedEvent)
    assertThat(supportedEvent.presentation.isVisible).isTrue()
    assertThat(supportedEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun archiveThreadActionInvokesArchiveCallback() {
    val context = editorContext()
    var archivedTargets: List<ArchiveThreadTarget>? = null

    val action = AgentSessionsArchiveThreadAction(
      resolveTreeContext = { null },
      resolveEditorContext = { context },
      canArchiveProvider = { true },
      archiveThreads = { targets -> archivedTargets = targets },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    val archivedTarget = checkNotNull(archivedTargets).single()
    assertThat(archivedTarget.path).isEqualTo(context.path)
    assertThat(archivedTarget.threadId).isEqualTo(context.sessionId)
    assertThat(archivedTarget.provider).isEqualTo(context.provider)
  }

  @Test
  fun selectInAgentThreadsActionEnsuresVisibilityAndActivatesToolWindow() {
    val context = editorContext()
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
    assertThat(ensuredThreadId).isEqualTo(context.sessionId)
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
  fun bindPendingCodexThreadActionVisibleAndEnabledWhenTargetIsAvailable() {
    val context = editorContext(
      threadIdentity = "codex:new-1",
      threadId = "",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatPendingTabRebindTarget(
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadTitle = "Recovered",
      threadActivity = com.intellij.agent.workbench.common.AgentThreadActivity.READY,
    )
    val action = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { context },
      resolveTarget = { target },
      rebindPendingTab = { _, _, _ -> true },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun bindPendingCodexThreadActionHiddenForNonPendingOrNonCodexContext() {
    val actionForNonPending = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { editorContext(isPendingThread = false) },
      resolveTarget = { error("resolveTarget must not be called for non-pending context") },
      rebindPendingTab = { _, _, _ -> false },
    )
    val nonPendingEvent = TestActionEvent.createTestEvent(actionForNonPending)
    actionForNonPending.update(nonPendingEvent)
    assertThat(nonPendingEvent.presentation.isEnabledAndVisible).isFalse()

    val actionForClaudePending = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = {
        editorContext(
          threadIdentity = "claude:new-1",
          provider = AgentSessionProvider.CLAUDE,
          sessionId = "new-1",
          isPendingThread = true,
        )
      },
      resolveTarget = { error("resolveTarget must not be called for non-codex pending context") },
      rebindPendingTab = { _, _, _ -> false },
    )
    val claudePendingEvent = TestActionEvent.createTestEvent(actionForClaudePending)
    actionForClaudePending.update(claudePendingEvent)
    assertThat(claudePendingEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun bindPendingCodexThreadActionInvokesSpecificRebindCallback() {
    val context = editorContext(
      threadIdentity = "codex:new-1",
      threadId = "",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatPendingTabRebindTarget(
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadTitle = "Recovered",
      threadActivity = com.intellij.agent.workbench.common.AgentThreadActivity.UNREAD,
    )
    var reboundPath: String? = null
    var reboundPendingIdentity: String? = null
    var reboundTarget: AgentChatPendingTabRebindTarget? = null

    val action = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { context },
      resolveTarget = { target },
      rebindPendingTab = { path, pendingIdentity, rebindTarget ->
        reboundPath = path
        reboundPendingIdentity = pendingIdentity
        reboundTarget = rebindTarget
        true
      },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    assertThat(reboundPath).isEqualTo(context.path)
    assertThat(reboundPendingIdentity).isEqualTo(context.threadIdentity)
    assertThat(reboundTarget).isEqualTo(target)
  }

  @Test
  fun copyThreadIdActionCopiesThreadId() {
    val context = editorContext(threadId = "thread-42", sessionId = "thread-42")
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
    assertThat(copiedThreadId).isEqualTo("thread-42")
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
  threadIdentity: String = "codex:thread-1",
  threadId: String = "thread-1",
  provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
  sessionId: String = "thread-1",
  isPendingThread: Boolean = false,
): AgentChatEditorTabActionContext {
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath("/tmp/project"),
    threadIdentity = threadIdentity,
    threadId = threadId,
    provider = provider,
    sessionId = sessionId,
    isPendingThread = isPendingThread,
  )
}
