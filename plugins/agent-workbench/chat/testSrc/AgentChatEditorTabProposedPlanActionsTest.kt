// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatEditorTabProposedPlanActionsTest {
  @Test
  fun proposedPlanActionsUpdateOnBackgroundThread() {
    assertThat(AgentChatPreviousProposedPlanAction().actionUpdateThread).isEqualTo(ActionUpdateThread.BGT)
    assertThat(AgentChatNextProposedPlanAction().actionUpdateThread).isEqualTo(ActionUpdateThread.BGT)
  }

  @Test
  fun proposedPlanActionHidesWithoutAgentChatFileEditor() {
    val action = AgentChatNextProposedPlanAction(
      canNavigate = { _, _ -> error("Update must not ask navigation state without an Agent chat editor") },
      navigate = { _, _ -> error("Action must not navigate without an Agent chat editor") },
    )
    val event = TestActionEvent.createTestEvent(action, SimpleDataContext.builder().build())

    action.update(event)
    action.actionPerformed(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun updateUsesAgentChatFileEditorFromDataContext() {
    withTestEditor { editor ->
      var capturedEditor: AgentChatFileEditor? = null
      var capturedDirection: AgentChatSemanticNavigationDirection? = null
      val action = AgentChatNextProposedPlanAction(
        canNavigate = { fileEditor, direction ->
          capturedEditor = fileEditor
          capturedDirection = direction
          true
        },
        navigate = { _, _ -> error("Update must not navigate") },
      )
      val event = testEvent(action, editor)

      action.update(event)

      assertThat(event.presentation.isEnabledAndVisible).isTrue()
      assertThat(capturedEditor).isSameAs(editor)
      assertThat(capturedDirection).isEqualTo(AgentChatSemanticNavigationDirection.NEXT)
    }
  }

  @Test
  fun actionPerformedUsesAgentChatFileEditorFromDataContext() {
    withTestEditor { editor ->
      var capturedEditor: AgentChatFileEditor? = null
      var capturedDirection: AgentChatSemanticNavigationDirection? = null
      val action = AgentChatPreviousProposedPlanAction(
        canNavigate = { _, _ -> error("Action execution must not ask navigation state") },
        navigate = { fileEditor, direction ->
          capturedEditor = fileEditor
          capturedDirection = direction
          true
        },
      )

      action.actionPerformed(testEvent(action, editor))

      assertThat(capturedEditor).isSameAs(editor)
      assertThat(capturedDirection).isEqualTo(AgentChatSemanticNavigationDirection.PREVIOUS)
    }
  }

  private fun withTestEditor(action: (AgentChatFileEditor) -> Unit) {
    val editor = AgentChatFileEditor(
      project = ProjectManager.getInstance().defaultProject,
      file = AgentChatVirtualFile(
        projectPath = "/tmp/agent-chat-project",
        threadIdentity = "codex:test-session",
        shellCommand = emptyList(),
        threadId = "test-session",
        threadTitle = "Test session",
        subAgentId = null,
      ),
    )
    try {
      action(editor)
    }
    finally {
      Disposer.dispose(editor)
    }
  }

  private fun testEvent(action: AnAction, editor: AgentChatFileEditor) =
    TestActionEvent.createTestEvent(
      action,
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
        .add(PlatformDataKeys.FILE_EDITOR, editor)
        .build(),
    )
}
