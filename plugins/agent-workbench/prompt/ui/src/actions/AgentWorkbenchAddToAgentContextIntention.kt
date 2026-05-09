// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.ui.AgentPromptAddToAgentContextActionService
import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.codeInsight.intention.AdvertisementAction
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

internal class AgentWorkbenchAddToAgentContextIntention : IntentionAction, AdvertisementAction, CustomizableIntentionAction {
  override fun getText(): String = AgentPromptBundle.message("intention.add.context.text")

  override fun getFamilyName(): String = AgentPromptBundle.message("intention.add.context.family.name")

  override fun isShowLightBulb(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null || project.isDisposed) return false
    if (editor.document.textLength == 0) return false
    return file.isPhysical
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) {
      return
    }

    val baseDataContext = DataManager.getInstance().getDataContext(editor.contentComponent)
    val dataContext = CustomizedDataContext.withSnapshot(baseDataContext) { sink: DataSink ->
      sink[CommonDataKeys.PROJECT] = project
      sink[CommonDataKeys.EDITOR] = editor
      sink[CommonDataKeys.PSI_FILE] = file
      file.virtualFile?.let { virtualFile ->
        sink[CommonDataKeys.VIRTUAL_FILE] = virtualFile
      }
    }
    project.service<AgentPromptAddToAgentContextActionService>().addToAgentContext(
      AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.AddToAgentContext.Intention",
        actionText = text,
        actionPlace = "intention",
        invokedAtMs = System.currentTimeMillis(),
        attributes = mapOf(AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext),
      )
    )
  }

  override fun startInWriteAction(): Boolean = false
}
