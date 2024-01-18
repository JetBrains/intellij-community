// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions

import com.intellij.coverage.CoverageBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ExternalReportImportAction : AnAction(
  CoverageBundle.message("coverage.import.report.action"),
  CoverageBundle.message("coverage.import.report.action.description"),
  AllIcons.ToolbarDecorator.Import
) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ExternalReportImportManager.getInstance(project).chooseAndOpenSuites()
  }
}
