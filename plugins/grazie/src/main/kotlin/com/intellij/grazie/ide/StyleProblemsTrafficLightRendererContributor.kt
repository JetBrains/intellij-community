package com.intellij.grazie.ide

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.UIController
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class StyleProblemsTrafficLightRendererContributor: TrafficLightRendererContributor {
  @RequiresBackgroundThread
  override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
    val project = editor.project ?: return null
    return StyleProblemTrafficLightRenderer(project, editor.document)
  }

  private class StyleProblemTrafficLightRenderer(project: Project, document: Document): TrafficLightRenderer(project, document) {
    override fun getErrorCounts(): IntArray {
      val errors = super.getErrorCounts()
      val errorIndex = severityRegistrar.findSeverityIndex(TextProblemSeverities.STYLE_ERROR)
      val warningIndex = severityRegistrar.findSeverityIndex(TextProblemSeverities.STYLE_WARNING)
      val suggestionIndex = severityRegistrar.findSeverityIndex(TextProblemSeverities.STYLE_SUGGESTION)
      if (errors[warningIndex] != 0) {
        errors[warningIndex] += errors[suggestionIndex]
        errors[suggestionIndex] = 0
      }
      if (errors[errorIndex] != 0) {
        errors[errorIndex] += errors[warningIndex] + errors[suggestionIndex]
        errors[warningIndex] = 0
        errors[suggestionIndex] = 0
      }
      return errors
    }

    /**
     * Have to provide own routine since [SeverityRegistrar.getSeverityIdx] is package private for some reason.
     */
    private fun SeverityRegistrar.findSeverityIndex(severity: HighlightSeverity): Int {
      // allSeverities collection is sorted in ascending order
      val index = allSeverities.binarySearch(severity)
      check(index != -1) { "Failed to find severity index in allSeverities" }
      return index
    }

    override fun createUIController(): UIController {
      return DefaultUIController()
    }
  }
}
