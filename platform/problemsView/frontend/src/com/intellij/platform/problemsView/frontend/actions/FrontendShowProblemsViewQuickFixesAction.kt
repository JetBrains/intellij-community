// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.toolWindow.HighlightingPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.getEditor
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.positionCaret
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionListStep
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.problemsView.frontend.FrontendHighlightingProblem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

internal class FrontendShowProblemsViewQuickFixesAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    val problem = node?.problem
    with(event.presentation) {
      val project = event.project
      isVisible = getApplication().isInternal || project != null && event.getData(ProblemsViewPanel.DATA_KEY) is HighlightingPanel
      isEnabled = isVisible && when (problem) {
        is FrontendHighlightingProblem -> isEnabled(event, problem)
        else -> false
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    when (val problem = node?.problem) {
      is FrontendHighlightingProblem -> actionPerformed(event, problem)
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

  private fun isEnabled(event: AnActionEvent, problem: FrontendHighlightingProblem): Boolean {
    if (!problem.hasQuickFixes()) return false

    val project = event.project ?: return false
    val editor = event.getData(ProblemsViewPanel.PREVIEW_DATA_KEY) ?: getEditor(problem.file, project, false) ?: return false

    return true
  }

  private fun actionPerformed(event: AnActionEvent, problem: FrontendHighlightingProblem) {
    val project = event.project ?: return
    val psiFile = PsiManager.getInstance(project).findFile(problem.file) ?: return

    val editor = event.getData(ProblemsViewPanel.PREVIEW_DATA_KEY) ?: getEditor(problem.file, project, true) ?: return

    val cachedIntentions = getCachedIntention(problem, project, psiFile, editor) ?: return

    positionCaret(problem.getQuickFixOffset(), editor)
    show(event, JBPopupFactory.getInstance().createListPopup(
      object : IntentionListStep(null, editor, psiFile, project, cachedIntentions, IntentionSource.PROBLEMS_VIEW) {}
      )
    )
  }

  private fun getCachedIntention(problem: FrontendHighlightingProblem, project: Project, psiFile: PsiFile, editor: Editor) : CachedIntentions? {
    val quickFixes = problem.getQuickFixes()
    if (quickFixes.isEmpty()) return null

    val intentions = ShowIntentionsPass.IntentionsInfo()

    intentions.offset = problem.getQuickFixOffset()
    intentions.intentionsToShow.addAll(
      quickFixes.map {
          quickFix -> dtoToIntentionActionDescriptor(quickFix, problem)
      }
    )

    val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFile, editor, intentions)
    if (cachedIntentions.intentions.isEmpty()) return null

    return cachedIntentions
  }
}
