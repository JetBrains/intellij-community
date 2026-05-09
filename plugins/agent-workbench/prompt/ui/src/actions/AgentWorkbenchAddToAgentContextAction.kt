// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.ui.AgentPromptAddToAgentContextActionService
import com.intellij.agent.workbench.prompt.ui.context.AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

internal class AgentWorkbenchAddToAgentContextAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<AgentPromptAddToAgentContextActionService>().addToAgentContext(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = ACTION_ID,
        actionText = e.presentation.text,
        actionPlace = e.place,
        invokedAtMs = System.currentTimeMillis(),
        attributes = mapOf(
          AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to e.dataContext,
          AGENT_PROMPT_INVOCATION_ACTION_EVENT_KEY to e,
        ),
      )
    )
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !project.isDisposed && isInvocationContextSupported(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun isInvocationContextSupported(e: AnActionEvent): Boolean {
    return when (e.place) {
      ActionPlaces.EDITOR_POPUP -> hasEditorContext(e)
      ActionPlaces.PROJECT_VIEW_POPUP -> hasProjectViewSelection(e)
      ActionPlaces.EDITOR_TAB_POPUP -> hasTabFile(e)
      else -> true
    }
  }

  private fun hasEditorContext(e: AnActionEvent): Boolean {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return false
    if (editor.document.textLength == 0) {
      return false
    }

    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
                      ?: FileDocumentManager.getInstance().getFile(editor.document)
    return virtualFile != null || e.getData(CommonDataKeys.PSI_FILE) != null
  }

  private fun hasProjectViewSelection(e: AnActionEvent): Boolean {
    val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (selectedFile != null) {
      return isLocalContextFile(selectedFile)
    }

    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
                          ?.toList()
                          ?.takeIf { it.isNotEmpty() }
                        ?: return false
    return selectedFiles.any(::isLocalContextFile)
  }

  private fun hasTabFile(e: AnActionEvent): Boolean {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    return isLocalContextFile(file)
  }

  private fun isLocalContextFile(file: VirtualFile): Boolean {
    return file.isInLocalFileSystem && file.path != "."
  }

  private companion object {
    private const val ACTION_ID: String = "AgentWorkbenchPrompt.AddToAgentContext"
  }
}
