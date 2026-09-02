// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.getEditor
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.openEditorIfNeeded
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils.positionCaret
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.IntentionListStep
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.problemsView.frontend.FrontendHighlightingPanel
import com.intellij.platform.problemsView.frontend.FrontendProblemsViewQuickFixService
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

internal class FrontendShowProblemsViewQuickFixesAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val isFrontendPanel = event.project != null && event.getData(ProblemsViewPanel.DATA_KEY) is FrontendHighlightingPanel
    val problem = (event.getData(SELECTED_ITEM) as? ProblemNode)?.problem as? HighlightingProblem

    event.presentation.isVisible = getApplication().isInternal || isFrontendPanel
    event.presentation.isEnabled = isFrontendPanel && problem?.hasQuickFixes() == true
  }

  override fun actionPerformed(event: AnActionEvent) {
    if (event.getData(ProblemsViewPanel.DATA_KEY) !is FrontendHighlightingPanel) return

    val project = event.project ?: return
    val problem = (event.getData(SELECTED_ITEM) as? ProblemNode)?.problem as? HighlightingProblem ?: return

    event.coroutineScope.launch {
      val intentions = getQuickFixes(problem, project)
      if (intentions.isEmpty()) return@launch

      withContext(Dispatchers.EDT) {
        showQuickFixes(event, problem, intentions)
      }
    }
  }

  private suspend fun getQuickFixes(problem: HighlightingProblem, project: Project): List<HighlightInfo.IntentionActionDescriptor> {
    if (IdeProductMode.isMonolith) {
      val info = problem.info ?: return emptyList()
      return buildList {
        info.findRegisteredQuickFix { action, _ ->
          add(action)
          null
        }
      }
    }
    else {
      return FrontendProblemsViewQuickFixService.getInstance(project).loadQuickFixes(problem)
    }
  }

  private fun showQuickFixes(
    event: AnActionEvent,
    problem: HighlightingProblem,
    intentions: List<HighlightInfo.IntentionActionDescriptor>
  ) {
    val project = event.project ?: return
    val offset = problem.getQuickFixOffset()
    val psiFile = PsiManager.getInstance(project).findFile(problem.file) ?: return
    val editor = event.getData(ProblemsViewPanel.PREVIEW_DATA_KEY) ?: run {
      val existingEditor = getEditor(problem.file, project) ?: return
      openEditorIfNeeded(problem.file, project, existingEditor) ?: return
    }
    val cachedIntentions = createCachedIntentions(
      intentions,
      project,
      psiFile,
      editor,
      offset,
    ) ?: return

    positionCaret(offset, editor)
    val popup = createPopup(project, psiFile, editor, cachedIntentions)
    show(event, popup)
  }

  private fun HighlightingProblem.hasQuickFixes(): Boolean {
    return HighlightInfo.fromRangeHighlighter(highlighter)?.hasQuickFixes() == true
  }

  private fun createCachedIntentions(
    intentionsToShow: List<HighlightInfo.IntentionActionDescriptor>,
    project: Project,
    psiFile: PsiFile,
    editor: Editor,
    offset: Int,
  ): CachedIntentions? {
    if (intentionsToShow.isEmpty()) return null

    val intentions = ShowIntentionsPass.IntentionsInfo()
    intentions.offset = offset
    intentions.intentionsToShow.addAll(intentionsToShow)
    return CachedIntentions.createAndUpdateActions(project, psiFile, editor, intentions)
      .takeIf { it.intentions.isNotEmpty() }
  }

  private fun createPopup(project: Project, psiFile: PsiFile, editor: Editor, intentions: CachedIntentions): JBPopup {
    return JBPopupFactory.getInstance().createListPopup(
      object : IntentionListStep(null, editor, psiFile, project, intentions, IntentionSource.PROBLEMS_VIEW) {
        override fun chooseActionAndInvoke(
          cachedAction: IntentionActionWithTextCaching,
          psiFile: PsiFile,
          project: Project,
          editor: Editor?,
        ) {
          editor?.contentComponent?.requestFocus()
          val modality = editor?.contentComponent?.let { ModalityState.stateForComponent(it) } ?: ModalityState.current()
          getApplication().invokeLater(
            {
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
                { super.chooseActionAndInvoke(cachedAction, psiFile, project, editor) },
                modality,
              )
            },
            modality,
            project.disposed,
          )
        }
      }
    )
  }

  private fun show(event: AnActionEvent, popup: JBPopup) {
    val mouse = event.inputEvent as? MouseEvent ?: return popup.showInBestPositionFor(event.dataContext)
    val point = mouse.locationOnScreen
    val panel = event.getData(ProblemsViewPanel.DATA_KEY)
    val button = mouse.source as? ActionButton
    if (panel == null || button == null) {
      popup.show(RelativePoint.fromScreen(point))
    }
    else {
      val popupPosition = if (panel.isVertical) AnchoredPoint.Anchor.BOTTOM_LEFT else AnchoredPoint.Anchor.TOP_RIGHT
      popup.show(AnchoredPoint(popupPosition, button))
    }
  }
}
