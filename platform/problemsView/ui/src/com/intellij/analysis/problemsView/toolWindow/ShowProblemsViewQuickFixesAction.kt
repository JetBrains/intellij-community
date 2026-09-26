// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.IntentionListStep
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.positionCaret
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.getEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

internal class ShowProblemsViewQuickFixesAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    val problem = node?.problem
    with(event.presentation) {
      val project = event.project
      isVisible = getApplication().isInternal || project != null && event.getData(ProblemsViewPanel.DATA_KEY) is HighlightingPanel
      isEnabled = isVisible && when (problem) {
        is HighlightingProblem -> isEnabled(event, problem)
        else -> false
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    when (val problem = node?.problem) {
      is HighlightingProblem -> actionPerformed(event, problem)
    }
  }

  private fun show(event: AnActionEvent, popup: JBPopup) {
    val mouse = event.inputEvent as? MouseEvent ?: return popup.showInBestPositionFor(event.dataContext)
    val point = mouse.locationOnScreen
    val panel = event.getData(ProblemsViewPanel.DATA_KEY)
    val button = mouse.source as? ActionButton
    if (panel == null || button == null) {
      popup.show(RelativePoint.fromScreen(point))
    } else {
      val popupPosition = when (panel.isVertical) {
        true -> AnchoredPoint.Anchor.BOTTOM_LEFT
        else -> AnchoredPoint.Anchor.TOP_RIGHT
      }
      popup.show(AnchoredPoint(popupPosition, button))
    }
  }

  private fun isEnabled(event: AnActionEvent, problem: HighlightingProblem): Boolean {
    return getCachedIntentions(event, problem, false) != null
  }

  private fun actionPerformed(event: AnActionEvent, problem: HighlightingProblem) {
    val intentions = getCachedIntentions(event, problem, true) ?: return
    val editor: Editor = intentions.editor ?: return

    positionCaret(intentions.offset, editor)
    show(event, JBPopupFactory.getInstance().createListPopup(
      object : IntentionListStep(null, editor, intentions.file, intentions.file.project, intentions, IntentionSource.PROBLEMS_VIEW) {
        override fun chooseActionAndInvoke(cachedAction: IntentionActionWithTextCaching, psiFile: PsiFile, project: Project, editor: Editor?) {
          editor?.contentComponent?.requestFocus()
          // hack until doWhenFocusSettlesDown will work as expected
          val modality = editor?.contentComponent?.let { ModalityState.stateForComponent(it) } ?: ModalityState.current()
          getApplication().invokeLater(
            {
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown({
                                                                            super.chooseActionAndInvoke(cachedAction, psiFile, project, editor)
                                                                          }, modality)
            }, modality, project.disposed)
        }
      }
    ))
  }

  private fun getCachedIntentions(event: AnActionEvent, problem: HighlightingProblem, showEditor: Boolean): CachedIntentions? {
    val psi = event.getData(CommonDataKeys.PSI_FILE) ?: return null
    val editor = event.getData(ProblemsViewPanel.PREVIEW_DATA_KEY) ?: getEditor(psi, showEditor) ?: return null
    val info = ShowIntentionsPass.IntentionsInfo()
    problem.info?.findRegisteredQuickFix { desc, _ ->
      info.intentionsToShow.add(desc)
      null
    }
    if (info.isEmpty) return null
    info.offset = problem.info?.actualStartOffset ?: -1

    val intentions = CachedIntentions.createAndUpdateActions(psi.project, psi, editor, info)
    if (intentions.intentions.isNotEmpty()) return intentions
    return null // actions can be removed after updating
  }
}
