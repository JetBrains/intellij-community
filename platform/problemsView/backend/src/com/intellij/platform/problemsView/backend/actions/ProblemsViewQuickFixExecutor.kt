// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend.actions

import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.problemsView.backend.ProblemLifetimeManager
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProblemsViewQuickFixExecutor {

  suspend fun executeQuickFix(project: Project, fileId: VirtualFileId, problemId: String, intentionId: String) {
    val file = fileId.virtualFile() ?: return
    if (!file.isValid) {
      thisLogger().debug("file ${file.name} is not valid, so quick fix can't be executed")
      return
    }

    val idManager = ProblemLifetimeManager.getInstance(project)
    val problem = (idManager.findProblemById(problemId)) as? HighlightingProblem ?: return
    val action = idManager.findIntentionById(intentionId) ?: return

    val (psiFile, editor) = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      val editor = psiFile?.let { ProblemsViewEditorUtils.getEditor(it, showEditor = true) }
      psiFile to editor
    }

    if (psiFile != null && editor != null) {
      editor.contentComponent.requestFocus()
      val modality = editor.contentComponent.let { ModalityState.stateForComponent(it) }

      getApplication().invokeLater(
        {
          IdeFocusManager.getInstance(project).doWhenFocusSettlesDown({
            ShowIntentionActionsHandler.chooseActionAndInvoke( psiFile,
                                                               editor,
                                                               action,
                                                               action.text,
                                                               problem.getQuickFixOffset(),
                                                               IntentionSource.PROBLEMS_VIEW)
                                                                      }, modality)
        }, modality, project.disposed)
    }
  }
}
