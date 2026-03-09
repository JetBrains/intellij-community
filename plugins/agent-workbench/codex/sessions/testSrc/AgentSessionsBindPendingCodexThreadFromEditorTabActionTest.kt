// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.codex.sessions.actions.AgentSessionsBindPendingCodexThreadFromEditorTabAction
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsBindPendingCodexThreadFromEditorTabActionTest {
  @Test
  fun actionVisibleAndEnabledWhenTargetIsAvailable() {
    val context = editorContext(
      threadIdentity = "codex:new-1",
      threadId = "",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatTabRebindTarget(
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadTitle = "Recovered",
      threadActivity = AgentThreadActivity.READY,
    )
    val action = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { context },
      resolveTarget = { target },
      rebindPendingTab = ::successfulPendingCodexRebindReport,
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionHiddenForNonPendingOrNonCodexContext() {
    val actionForNonPending = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { editorContext(isPendingThread = false) },
      resolveTarget = { error("resolveTarget must not be called for non-pending context") },
      rebindPendingTab = ::failingPendingCodexRebindReport,
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
      rebindPendingTab = ::failingPendingCodexRebindReport,
    )
    val claudePendingEvent = TestActionEvent.createTestEvent(actionForClaudePending)
    actionForClaudePending.update(claudePendingEvent)
    assertThat(claudePendingEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun actionInvokesSpecificRebindCallback() {
    val context = editorContext(
      threadIdentity = "codex:new-1",
      threadId = "",
      provider = AgentSessionProvider.CODEX,
      sessionId = "new-1",
      isPendingThread = true,
    )
    val target = AgentChatTabRebindTarget(
      threadIdentity = "codex:thread-42",
      threadId = "thread-42",
      shellCommand = listOf("codex", "resume", "thread-42"),
      threadTitle = "Recovered",
      threadActivity = AgentThreadActivity.UNREAD,
    )
    var reboundPath: String? = null
    var reboundPendingTabKey: String? = null
    var reboundPendingIdentity: String? = null
    var reboundTarget: AgentChatTabRebindTarget? = null

    val action = AgentSessionsBindPendingCodexThreadFromEditorTabAction(
      resolveContext = { context },
      resolveTarget = { target },
      rebindPendingTab = { requestsByPath ->
        val entry = requestsByPath.entries.single()
        val request = entry.value.single()
        reboundPath = entry.key
        reboundPendingTabKey = request.pendingTabKey
        reboundPendingIdentity = request.pendingThreadIdentity
        reboundTarget = request.target
        successfulPendingCodexRebindReport(requestsByPath)
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
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsBindPendingCodexThreadFromEditorTabAction::class.java)

    val entries = editorTabPopupEntries(actionManager)
    val goToSourceIndex = entries.requiredIndex("AgentWorkbenchSessions.GoToSourceProjectFromEditorTab")
    val bindPendingIndex = entries.requiredIndex("AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab")
    val closeEditorsGroupIndex = entries.requiredIndex("CloseEditorsGroup")

    assertThat(goToSourceIndex).isLessThan(bindPendingIndex)
    assertThat(bindPendingIndex).isLessThan(closeEditorsGroupIndex)
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
    subAgentId = null,
  )
}

private fun successfulPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  val requestedBindings = requestsByPath.values.sumOf { it.size }
  return AgentChatPendingCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = requestedBindings,
    reboundFiles = requestedBindings,
    updatedPresentations = requestedBindings,
    outcomesByPath = emptyMap(),
  )
}

private fun failingPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  val requestedBindings = requestsByPath.values.sumOf { it.size }
  val outcomesByPath = requestsByPath.mapValues { (path, requests) ->
    requests.map { request ->
      AgentChatPendingCodexTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN,
        reboundFiles = 0,
      )
    }
  }
  return AgentChatPendingCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = outcomesByPath,
  )
}

private fun editorTabPopupEntries(actionManager: ActionManager): List<String> {
  val group = actionManager.getAction("EditorTabPopupMenu") as com.intellij.openapi.actionSystem.ActionGroup
  return group.getChildren(TestActionEvent.createTestEvent()).mapNotNull { action ->
    when (action) {
      is com.intellij.openapi.actionSystem.Separator -> "<separator>"
      else -> actionManager.getId(action)
    }
  }
}

private fun List<String>.requiredIndex(id: String): Int {
  val index = indexOf(id)
  assertThat(index).withFailMessage("Expected action '%s' in %s", id, this).isGreaterThanOrEqualTo(0)
  return index
}
