// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.actions

import com.intellij.agent.workbench.ai.review.model.AIReviewResult
import com.intellij.agent.workbench.ai.review.ui.AIReviewFileProblem
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemNode
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.FileNode
import com.intellij.analysis.problemsView.toolWindow.ProblemNodeI
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Base action for posting AI review problems as drafts to a collaboration platform.
 */
@ApiStatus.Internal
abstract class AIReviewPostDraftAction : DumbAwareAction() {

  protected abstract fun getNotificationGroupId(): String

  protected abstract fun isAvailable(project: Project): Boolean

  protected abstract fun updatePresentation(e: AnActionEvent, project: Project)

  protected abstract fun getConfirmTitle(): @Nls String

  protected abstract fun getConfirmMessage(count: Int): @Nls String

  protected abstract fun getProgressTitle(count: Int): @Nls String

  protected abstract suspend fun postProblems(project: Project, problems: List<AIReviewFileProblem>)

  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  final override fun update(e: AnActionEvent) {
    val project = e.project
    val session = e.getData(AIReviewProblemsViewPanel.SESSION_KEY)

    if (project == null || session == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    updatePresentation(e, project)

    if (isBusy(session.viewModel)) {
      e.presentation.isEnabled = false
      return
    }

    if (session.problemsHolder.getCollectedProblems().isEmpty()) {
      e.presentation.isEnabled = false
      return
    }

    if (!isAvailable(project)) {
      e.presentation.isEnabled = false
    }
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val problems = collectProblems(e)
    if (problems.isEmpty()) return

    val confirmed = MessageDialogBuilder.okCancel(
      getConfirmTitle(),
      getConfirmMessage(problems.size),
    ).ask(project)
    if (!confirmed) return

    currentThreadCoroutineScope().launch {
      withBackgroundProgress(project, getProgressTitle(problems.size)) {
        postProblems(project, problems)
      }
    }
  }

  protected fun collectProblems(e: AnActionEvent): List<AIReviewFileProblem> {
    val nodes = e.getData(SELECTED_ITEMS)
    val problems = mutableListOf<AIReviewFileProblem>()

    if (nodes.isNullOrEmpty()) return problems

    for (node in nodes) {
      if (node is ProblemNodeI) {
        (node.problem as? AIReviewFileProblem)?.let { problems += it }
      }
      else if (node is FileNode) {
        val children = node.getChildren().filterIsInstance<AIReviewProblemNode>()
        for (child in children) {
          problems += child.problem
        }
      }
    }

    return problems
  }

  protected fun formatProblemText(problem: AIReviewResult.Problem): String {
    val severity = problem.severity.name
    val text = StringBuilder()
    text.append("**[$severity]** ${problem.message}")
    if (problem.reasoning.isNotBlank()) {
      text.append("\n\n${problem.reasoning}")
    }
    return text.toString()
  }

  protected fun showNotification(project: Project, @Nls content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup(getNotificationGroupId())
      .createNotification(content, type)
      .notify(project)
  }
}
