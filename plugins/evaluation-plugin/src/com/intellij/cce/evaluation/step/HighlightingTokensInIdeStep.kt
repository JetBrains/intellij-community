package com.intellij.cce.evaluation.step

import com.intellij.cce.highlighter.Highlighter
import com.intellij.cce.metric.SuggestionsComparator
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project

class HighlightingTokensInIdeStep(
  private val suggestionsComparator: SuggestionsComparator,
  project: Project,
  isHeadless: Boolean) : BackgroundEvaluationStep(project, isHeadless) {
  override val name: String = "Highlighting tokens in IDE"

  override val description: String = "Highlight tokens on which completion was called"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val highlighter = Highlighter(project, suggestionsComparator)
    val sessionFiles = workspace.sessionsStorage.getSessionFiles()
    for (file in sessionFiles) {
      val sessionsInfo = workspace.sessionsStorage.getSessions(file.first)
      highlighter.highlight(sessionsInfo.sessions)
    }
    return workspace
  }
}