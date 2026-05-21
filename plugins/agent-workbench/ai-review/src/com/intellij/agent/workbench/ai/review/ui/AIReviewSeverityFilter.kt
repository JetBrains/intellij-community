// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.agent.workbench.ai.review.model.AIReviewResult
import com.intellij.agent.workbench.ai.review.model.displayName
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.util.ui.tree.TreeUtil.promiseExpandAll
import org.jetbrains.annotations.Nls

internal class AIReviewProblemFilter(val state: ProblemsViewState) : (AIReviewFileProblem) -> Boolean {
  override fun invoke(problem: AIReviewFileProblem): Boolean {
    return !(state.hideBySeverity.contains(problem.severity.ordinal))
  }
}

internal class AIReviewSeverityFiltersActionGroup : DumbAware, DefaultActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && e.getData(AIReviewProblemsViewPanel.PANEL_KEY) != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return EMPTY_ARRAY
    if (project.isDisposed) return EMPTY_ARRAY

    val severities = AIReviewResult.Severity.entries.reversed()
      .filter {
        it == AIReviewResult.Severity.Error
        || it == AIReviewResult.Severity.StrongWarning
        || it == AIReviewResult.Severity.Warning
        || it == AIReviewResult.Severity.WeakWarning
      }

    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY)
    if (panel == null) return EMPTY_ARRAY

    return severities.mapTo(ArrayList<AnAction>()) {
      AIReviewSeverityFilterAction(it.displayName, it.ordinal, panel)
    }.toTypedArray()
  }
}

private class AIReviewSeverityFilterAction(@Nls name: String, val severity: Int, panel: AIReviewProblemsViewPanel)
  : AIReviewSeverityFilterActionBase(name, panel) {

  override fun isSelected(event: AnActionEvent) = !panel.state.hideBySeverity.contains(severity)

  override fun updateState(selected: Boolean): Boolean {
    val state = panel.state
    return if (selected) state.removeSeverity(severity) else state.addSeverity(severity)
  }
}

private abstract class AIReviewSeverityFilterActionBase(
  val filterName: @Nls String,
  val panel: AIReviewProblemsViewPanel,
) : DumbAwareToggleAction(filterName) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  abstract fun updateState(selected: Boolean): Boolean

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val changed = updateState(selected)
    if (changed) {
      val wasEmpty = panel.tree.isEmpty
      panel.state.intIncrementModificationCount()
      panel.treeModel.structureChanged(null)
      if (wasEmpty) {
        promiseExpandAll(panel.tree)
      }
    }

    val session = panel.session
    session.viewModel.setFiltersAppliedState(filterName, selected)
  }
}
