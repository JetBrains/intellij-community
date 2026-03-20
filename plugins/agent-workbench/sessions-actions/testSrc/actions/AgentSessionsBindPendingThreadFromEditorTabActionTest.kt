// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsBindPendingThreadFromEditorTabActionTest {
  @Test
  fun actionVisibleAndEnabledWhenTargetIsAvailable() {
    val normalizedPath = normalizeAgentWorkbenchPath("/tmp/project")
    val context = editorContext(
      path = normalizedPath,
      threadIdentity = "codex:new-1",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatTabRebindTarget(
      projectPath = normalizedPath,
      provider = AgentSessionProvider.CODEX,
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      threadTitle = "Recovered",
      threadActivity = AgentThreadActivity.READY,
    )
    val action = AgentSessionsBindPendingThreadFromEditorTabAction(
      resolveContext = { context },
      resolveProvider = { AgentSessionProvider.CODEX },
      resolveTarget = { _, _ -> target },
      rebindPendingTab = { _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionHiddenForNonPendingContext() {
    val actionForNonPending = AgentSessionsBindPendingThreadFromEditorTabAction(
      resolveContext = { editorContext(isPendingThread = false) },
      resolveProvider = { null },
      resolveTarget = { _, _ -> error("resolveTarget must not be called for non-pending context") },
      rebindPendingTab = { _, _ -> error("rebindPendingTab must not be called for non-pending context") },
    )
    val nonPendingEvent = TestActionEvent.createTestEvent(actionForNonPending)
    actionForNonPending.update(nonPendingEvent)
    assertThat(nonPendingEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun actionVisibleAndEnabledForClaudeWhenTargetIsAvailable() {
    val normalizedPath = normalizeAgentWorkbenchPath("/tmp/project")
    val context = editorContext(
      path = normalizedPath,
      threadIdentity = "claude:new-1",
      provider = AgentSessionProvider.CLAUDE,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatTabRebindTarget(
      projectPath = normalizedPath,
      provider = AgentSessionProvider.CLAUDE,
      threadIdentity = "claude:thread-42",
      threadId = "thread-42",
      threadTitle = "Recovered Claude",
      threadActivity = AgentThreadActivity.READY,
    )
    val action = AgentSessionsBindPendingThreadFromEditorTabAction(
      resolveContext = { context },
      resolveProvider = { AgentSessionProvider.CLAUDE },
      resolveTarget = { _, _ -> target },
      rebindPendingTab = { _, _ -> },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionInvokesSpecificRebindCallback() {
    val normalizedPath = normalizeAgentWorkbenchPath("/tmp/project")
    val context = editorContext(
      path = normalizedPath,
      threadIdentity = "codex:new-1",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatTabRebindTarget(
      projectPath = normalizedPath,
      provider = AgentSessionProvider.CODEX,
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      threadTitle = "Recovered",
      threadActivity = AgentThreadActivity.UNREAD,
    )
    var reboundPath: String? = null
    var reboundPendingTabKey: String? = null
    var reboundPendingIdentity: String? = null
    var reboundTarget: AgentChatTabRebindTarget? = null

    val action = AgentSessionsBindPendingThreadFromEditorTabAction(
      resolveContext = { context },
      resolveProvider = { AgentSessionProvider.CODEX },
      resolveTarget = { _, _ -> target },
      rebindPendingTab = { _, requestsByPath ->
        val entry = requestsByPath.entries.single()
        val request = entry.value.single()
        reboundPath = entry.key
        reboundPendingTabKey = request.pendingTabKey
        reboundPendingIdentity = request.pendingThreadIdentity
        reboundTarget = request.target
      },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    assertThat(reboundPath).isEqualTo(context.path)
    assertThat(reboundPendingTabKey).isEqualTo(context.tabKey)
    assertThat(reboundPendingIdentity).isEqualTo(context.threadIdentity)
    assertThat(reboundTarget).isEqualTo(target)
  }

  @Test
  fun actionIsRegisteredInEditorTabPopupMenu() {
    assertThat(actionsDescriptor())
      .contains("id=\"AgentWorkbenchSessions.BindPendingAgentThreadFromEditorTab\"")
      .contains(
        "<add-to-group group-id=\"EditorTabPopupMenu\" anchor=\"after\" relative-to-action=\"AgentWorkbenchSessions.GoToSourceProjectFromEditorTab\"/>",
      )
  }
}

private fun editorContext(
  path: String = "/tmp/project",
  tabKey: String = "tab-pending-1",
  threadIdentity: String = "codex:thread-1",
  provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
  sessionId: String = "thread-1",
  isPendingThread: Boolean = false,
): AgentChatEditorTabActionContext {
  val threadCoordinates = provider
    ?.takeIf { sessionId.isNotBlank() }
    ?.let { resolvedProvider ->
      AgentChatThreadCoordinates(
        provider = resolvedProvider,
        sessionId = sessionId,
        isPending = isPendingThread,
      )
    }
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath(path),
    tabKey = tabKey,
    threadIdentity = threadIdentity,
    threadCoordinates = threadCoordinates,
    sessionActionTarget = null,
  )
}

private fun actionsDescriptor(): String {
  return checkNotNull(AgentSessionsBindPendingThreadFromEditorTabActionTest::class.java.classLoader.getResource("intellij.agent.workbench.sessions.actions.xml")) {
    "Module descriptor intellij.agent.workbench.sessions.actions.xml is missing"
  }.readText()
}
