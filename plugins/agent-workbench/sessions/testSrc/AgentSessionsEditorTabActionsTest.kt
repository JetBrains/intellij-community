// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsEditorTabActionsTest {
  @Test
  fun toThreadContextFromEditorContext() {
    val editorContext = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = normalizeAgentWorkbenchPath("/tmp/project"),
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
    )

    val context = toThreadEditorTabActionContext(editorContext)

    assertThat(context).isNotNull
    assertThat(context!!.path).isEqualTo(editorContext.path)
    assertThat(context.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(context.threadId).isEqualTo("thread-1")
    assertThat(context.thread.id).isEqualTo("thread-1")
    assertThat(context.thread.provider).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun toThreadContextReturnsNullForInvalidIdentity() {
    val editorContext = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = normalizeAgentWorkbenchPath("/tmp/project"),
      threadIdentity = "invalid-identity",
      threadId = "thread-1",
    )

    val context = toThreadEditorTabActionContext(editorContext)

    assertThat(context).isNull()
  }

  @Test
  fun toThreadContextReturnsNullForPendingIdentity() {
    val editorContext = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = normalizeAgentWorkbenchPath("/tmp/project"),
      threadIdentity = "codex:new-123",
      threadId = "new-123",
    )

    val context = toThreadEditorTabActionContext(editorContext)

    assertThat(context).isNull()
  }

  @Test
  fun archiveThreadActionVisibleAndEnabledOnlyWhenProviderSupportsArchive() {
    val context = threadContext()

    val unsupported = AgentSessionsArchiveThreadFromEditorTabAction(
      resolveContext = { context },
      canArchiveThread = { false },
      archiveThread = { _, _ -> },
    )
    val unsupportedEvent = TestActionEvent.createTestEvent(unsupported)
    unsupported.update(unsupportedEvent)
    assertThat(unsupportedEvent.presentation.isVisible).isTrue()
    assertThat(unsupportedEvent.presentation.isEnabled).isFalse()

    val supported = AgentSessionsArchiveThreadFromEditorTabAction(
      resolveContext = { context },
      canArchiveThread = { true },
      archiveThread = { _, _ -> },
    )
    val supportedEvent = TestActionEvent.createTestEvent(supported)
    supported.update(supportedEvent)
    assertThat(supportedEvent.presentation.isVisible).isTrue()
    assertThat(supportedEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun archiveThreadActionInvokesArchiveCallback() {
    val context = threadContext()
    var archivedPath: String? = null
    var archivedThread: AgentSessionThread? = null

    val action = AgentSessionsArchiveThreadFromEditorTabAction(
      resolveContext = { context },
      canArchiveThread = { true },
      archiveThread = { path, thread ->
        archivedPath = path
        archivedThread = thread
      },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    assertThat(archivedPath).isEqualTo(context.path)
    assertThat(archivedThread?.id).isEqualTo(context.threadId)
    assertThat(archivedThread?.provider).isEqualTo(context.provider)
  }

  @Test
  fun selectInAgentThreadsActionEnsuresVisibilityAndActivatesToolWindow() {
    val context = threadContext()
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
  fun copyThreadIdActionCopiesThreadId() {
    val context = editorContext(threadId = "thread-42")
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
  fun copyThreadIdActionDisabledWhenThreadIdBlank() {
    val context = editorContext(threadId = "")
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
    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = normalizeAgentWorkbenchPath("/tmp/project"),
      threadIdentity = "codex:new-123",
      threadId = "thread-42",
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

private fun editorContext(threadId: String): AgentChatEditorTabActionContext {
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath("/tmp/project"),
    threadIdentity = "codex:thread-1",
    threadId = threadId,
  )
}

private fun threadContext(): AgentChatThreadEditorTabActionContext {
  val provider = AgentSessionProvider.CODEX
  val threadId = "thread-1"
  return AgentChatThreadEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath("/tmp/project"),
    provider = provider,
    threadId = threadId,
    thread = AgentSessionThread(
      id = threadId,
      title = "Thread 1",
      updatedAt = 0L,
      archived = false,
      provider = provider,
    ),
  )
}
