// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.actions.AnnotationData
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.await
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.history.canShowFileHistory
import com.intellij.vcs.log.history.showFileHistoryUi
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToHash
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.launch

private class ShowInFileHistoryAnnotationActionProvider : AnnotationGutterActionProvider {
  override fun createAction(annotation: FileAnnotation): AnAction {
    return ShowInFileHistoryAnnotationAction(annotation)
  }
}

private class ShowInFileHistoryAnnotationAction(private val annotation: FileAnnotation) :
  DumbAwareAction(VcsLogBundle.message("vcs.log.action.show.in.file.history.text")) {

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
    val annotatedRevisionNumber = annotationData?.revisionNumber?.asString()

    e.presentation.isEnabledAndVisible = canShowFileHistory(project, listOf(annotatedFilePath), annotatedRevisionNumber)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = annotation.file ?: return
    val lineRevisionNumber = getLineRevisionNumber(e) ?: return

    val annotationData = AnnotationData.extractFrom(project, file)
    val annotatedFilePath = annotationData?.filePath ?: VcsUtil.getFilePath(file)
    val annotatedRevisionNumber = annotationData?.revisionNumber?.asString()

    val ui = showFileHistoryUi(project, listOf(annotatedFilePath), annotatedRevisionNumber) ?: return
    val future = ui.jumpToHash(lineRevisionNumber, false, true)

    VcsProjectLog.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project,
                             VcsLogBundle.message("file.history.show.commit.in.history.process",
                                                  VcsLogUtil.getShortHash(lineRevisionNumber)), true) {
        future.await()
      }
    }
  }

  private fun getLineRevisionNumber(e: AnActionEvent): String? {
    val lineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(e.dataContext)
    return annotation.getLineRevisionNumber(lineNumber)?.asString()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
