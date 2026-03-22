// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.rearrangeByPromotersImpl
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentWorkbenchPromptActionPromoterTest {
  private val promoter = AgentWorkbenchPromptShortcutActionPromoter()

  @Test
  fun promotesGlobalPromptAheadOfAiAssistantInEditorContext() {
    val promptAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.PROMPT_ACTION_ID)
    val aiAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.AI_ASSISTANT_EDITOR_ACTION_ID)

    try {
      runInEdtAndWait {
        withEditorDataContext(ProjectManager.getInstance().defaultProject) { dataContext ->
          val result = rearrangeByPromotersImpl(
            listOf(aiAction.action, promptAction.action),
            dataContext,
            listOf(promoter),
          )

          assertThat(result).containsExactly(promptAction.action, aiAction.action)
        }
      }
    }
    finally {
      aiAction.dispose()
      promptAction.dispose()
    }
  }

  @Test
  fun leavesOrderUnchangedWithoutEditorContext() {
    val promptAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.PROMPT_ACTION_ID)
    val aiAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.AI_ASSISTANT_EDITOR_ACTION_ID)

    try {
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
        .build()

      val result = rearrangeByPromotersImpl(
        listOf(aiAction.action, promptAction.action),
        dataContext,
        listOf(promoter),
      )

      assertThat(result).containsExactly(aiAction.action, promptAction.action)
    }
    finally {
      aiAction.dispose()
      promptAction.dispose()
    }
  }

  @Test
  fun leavesOrderUnchangedWhenAiAssistantActionIsMissing() {
    val promptAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.PROMPT_ACTION_ID)

    try {
      runInEdtAndWait {
        withEditorDataContext(ProjectManager.getInstance().defaultProject) { dataContext ->
          val result = rearrangeByPromotersImpl(
            listOf(promptAction.action),
            dataContext,
            listOf(promoter),
          )

          assertThat(result).containsExactly(promptAction.action)
        }
      }
    }
    finally {
      promptAction.dispose()
    }
  }

  @Test
  fun doesNotPromoteAutoSelectActionAgainstAiAssistant() {
    val autoSelectAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.AUTO_SELECT_ACTION_ID)
    val aiAction = registerActionIfNeeded(AgentWorkbenchPromptShortcutActionPromoter.AI_ASSISTANT_EDITOR_ACTION_ID)

    try {
      runInEdtAndWait {
        withEditorDataContext(ProjectManager.getInstance().defaultProject) { dataContext ->
          val result = rearrangeByPromotersImpl(
            listOf(aiAction.action, autoSelectAction.action),
            dataContext,
            listOf(promoter),
          )

          assertThat(result).containsExactly(aiAction.action, autoSelectAction.action)
        }
      }
    }
    finally {
      aiAction.dispose()
      autoSelectAction.dispose()
    }
  }

  private fun withEditorDataContext(project: Project, block: (DataContext) -> Unit) {
    val editorFactory = EditorFactory.getInstance()
    val editor = editorFactory.createEditor(editorFactory.createDocument("prompt"))
    try {
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .build()

      block(dataContext)
    }
    finally {
      editorFactory.releaseEditor(editor)
    }
  }

  private fun registerActionIfNeeded(actionId: String): RegisteredAction {
    val actionManager = ActionManager.getInstance()
    val existing = actionManager.getAction(actionId)
    if (existing != null) {
      return RegisteredAction(existing)
    }

    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    actionManager.registerAction(actionId, action)
    return RegisteredAction(action) {
      actionManager.unregisterAction(actionId)
    }
  }

  private class RegisteredAction(
    val action: AnAction,
    private val cleanup: () -> Unit = {},
  ) {
    fun dispose() {
      cleanup()
    }
  }
}
