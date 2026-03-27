// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.actions

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.model.AIReviewResult
import com.intellij.agent.workbench.ai.review.model.AIReviewSession
import com.intellij.agent.workbench.ai.review.ui.AIReviewProblemsViewPanel
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal class AIReviewExportAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val session = e.getData(AIReviewProblemsViewPanel.SESSION_KEY)

    e.presentation.text = getActionText()
    e.presentation.description = getActionDescription()
    e.presentation.isVisible = true
    e.presentation.isEnabled = session != null
                               && !isBusy(session.viewModel)
                               && session.problemsHolder.getCollectedProblems().isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = e.getData(AIReviewProblemsViewPanel.SESSION_KEY) ?: return
    currentThreadCoroutineScope().launch {
      withContext(Dispatchers.Default) {
        performExport(session)
      }
    }
  }

  private suspend fun performExport(session: AIReviewSession) {
    val project = session.project
    val problemsByPath = session.problemsHolder.getCollectedProblems()
    val scratchFile = withContext(Dispatchers.EDT) {
      ScratchFileService.getInstance().findFile(
        RootType.findById("scratches"), "ai-review-${UUID.randomUUID()}.md",
        ScratchFileService.Option.create_new_always)
    }

    val reviewResults = buildMarkdownReport(problemsByPath)

    edtWriteAction {
      scratchFile.writeText(reviewResults)
    }

    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, scratchFile), true)
    }
  }

  private fun buildMarkdownReport(problemsByPath: Map<String, List<AIReviewResult.Problem>>): String {
    val sb = StringBuilder()
    sb.appendLine("# ${AIReviewBundle.message("aiReview.problems.export.results")}")
    sb.appendLine()

    if (problemsByPath.isEmpty()) {
      sb.appendLine(AIReviewBundle.message("aiReview.problems.no.problems.found"))
    }
    else {
      val totalCount = problemsByPath.values.sumOf { it.size }
      sb.appendLine(AIReviewBundle.message("aiReview.problems.export.found.problems", totalCount))
      sb.appendLine()

      problemsByPath
        .toSortedMap()
        .forEach { (filePath, problemsInFile) ->
          sb.appendLine("## $filePath")
          problemsInFile
            .asSequence()
            .sortedWith(compareByDescending<AIReviewResult.Problem> { it.severity.ordinal }.thenBy { it.lineStart })
            .forEachIndexed { index, problem ->
              val isSingleLineProblem = problem.lineStart == problem.lineEnd
              val lineOrLines = AIReviewBundle.message("aiReview.problems.export.found.lines", if (isSingleLineProblem) 1 else 2)
              val lineRange =
                if (isSingleLineProblem) "$lineOrLines ${problem.lineStart}" else "$lineOrLines ${problem.lineStart}-${problem.lineEnd}"

              val severity = problem.severity.name
              val title = problem.message
              val description = problem.reasoning.takeIf { it.isNotBlank() }

              sb.appendLine("${index + 1}. **$severity** $lineRange: $title")
              if (description != null) {
                sb.appendLine("   $description")
              }
            }
          sb.appendLine()
        }
    }

    return sb.toString()
  }

  private fun getActionText(): @NlsActions.ActionText String =
    AIReviewBundle.message("aiReview.export.action.text")

  private fun getActionDescription(): @NlsActions.ActionDescription String =
    AIReviewBundle.message("aiReview.export.action.description")
}
