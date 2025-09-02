// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.actions.AnnotationData
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcsUtil.VcsUtil

private class ShowInFileHistoryAnnotationActionProvider : AnnotationGutterActionProvider {
  override fun createAction(annotation: FileAnnotation): AnAction {
    val service = annotation.project.service<VcsLogFileHistoryProvider>()
    return ShowInFileHistoryAnnotationAction(service, annotation)
  }
}

private class ShowInFileHistoryAnnotationAction(
  private val service: VcsLogFileHistoryProvider,
  private val annotation: FileAnnotation,
) : DumbAwareAction(VcsLogBundle.message("vcs.log.action.show.in.file.history.text")) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = annotation.file
    val lineRevisionNumber = getLineRevisionNumber(e)
    if (project == null || file == null || lineRevisionNumber == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val annotationData = AnnotationData.extractFrom(project, file)
    val annotatedFilePath = annotationData?.filePath ?: VcsUtil.getFilePath(file)
    val annotatedRevisionNumber = annotationData?.revisionNumber

    e.presentation.isEnabledAndVisible = service.canShowFileHistory(listOf(annotatedFilePath), annotatedRevisionNumber)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = annotation.file ?: return
    val lineRevisionNumber = getLineRevisionNumber(e) ?: return

    val annotationData = AnnotationData.extractFrom(project, file)
    val annotatedFilePath = annotationData?.filePath ?: VcsUtil.getFilePath(file)
    val annotatedRevisionNumber = annotationData?.revisionNumber

    service.showFileHistory(listOf(annotatedFilePath), annotatedRevisionNumber, lineRevisionNumber)
  }

  private fun getLineRevisionNumber(e: AnActionEvent): VcsRevisionNumber? {
    val lineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(e.dataContext)
    return annotation.getLineRevisionNumber(lineNumber)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
