// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * Context menu entry for the Project view (and other trees exposing [CommonDataKeys.VIRTUAL_FILE]).
 */
class JUnitViewReportAsTestResultsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || file == null || !file.isValid || file.isDirectory) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = JUnitReportXmlDetector.looksLikeJUnitReportFile(file)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project: Project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    AbstractImportTestsAction.doImport(project, file, null)
  }
}
