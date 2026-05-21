// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.actions

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.ui.AIReviewFileProblem
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemNode
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INITIAL_TEXT_DATA_KEY
import com.intellij.analysis.problemsView.toolWindow.FileNode
import com.intellij.analysis.problemsView.toolWindow.ProblemNodeI
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Action that takes [AIReviewFileProblem]s and opens the Agent Workbench prompt to fix them.
 * If the prompt action is unavailable, it falls back to navigating to the first selected problem.
 */
@ApiStatus.Internal
open class AIReviewFixAction : DumbAwareAction() {

  companion object {
    private const val AGENT_WORKBENCH_PROMPT_AUTO_SELECT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect"
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val node = e.getData(SELECTED_ITEM)
    val panel = e.getData(AIReviewProblemsViewPanel.PANEL_KEY)

    val visible = project != null && panel != null
    e.presentation.isEnabledAndVisible = visible

    if (!visible) return

    e.presentation.text = getActionText(1)
    e.presentation.description = getActionDescription(1)
    e.presentation.icon = AllIcons.Actions.IntentionBulb

    if (node == null) {
      e.presentation.isEnabled = false
      return
    }

    val (_, problems) = getProblems(node) ?: run {
      e.presentation.isEnabled = false
      return
    }

    e.presentation.text = getActionText(problems.size)
    e.presentation.description = getActionDescription(problems.size)
    e.presentation.isEnabled = problems.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val node = e.getData(SELECTED_ITEM) ?: return
    val (problemFile, problems) = getProblems(node) ?: return

    if (openPrompt(e)) return

    val problem = problems.firstOrNull() ?: return
    val line = problem.line
    val column = problem.column

    FileEditorManager.getInstance(project).openTextEditor(
      OpenFileDescriptor(project, problemFile, line, column),
      true
    )
  }

  private fun openPrompt(e: AnActionEvent): Boolean {
    val promptAction = ActionManager.getInstance().getAction(AGENT_WORKBENCH_PROMPT_AUTO_SELECT_ACTION_ID) ?: return false

    val dataContext = CustomizedDataContext.withSnapshot(e.dataContext) { sink: DataSink ->
      sink[AGENT_PROMPT_INITIAL_TEXT_DATA_KEY] = "Fix selected problems\n"
    }
    val promptEvent = AnActionEvent.createEvent(promptAction, dataContext, null, e.place, ActionUiKind.NONE, null)
    promptAction.actionPerformed(promptEvent)
    return true
  }

  private fun getActionText(problemCount: Int): @NlsActions.ActionText String =
    AIReviewBundle.message("aiReview.fix.action.text", problemCount)

  private fun getActionDescription(problemCount: Int): @NlsActions.ActionDescription String =
    AIReviewBundle.message("aiReview.fix.action.description", problemCount)

  private fun getProblems(node: Any): Pair<VirtualFile, List<AIReviewFileProblem>>? {
    val problems = mutableListOf<AIReviewFileProblem>()
    var problemFile: VirtualFile? = null

    if (node is ProblemNodeI) {
      val problem = node.problem as? AIReviewFileProblem ?: return null
      problems += problem
      problemFile = problem.file
    }
    else if (node is FileNode) {
      problemFile = node.file
      problems += node.getChildren().filterIsInstance<AIReviewProblemNode>().map { it.problem }.toList()
    }

    if (problemFile == null || problems.isEmpty()) return null

    return problemFile to problems
  }
}
