// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent

class CodeCleanupCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler = CodeCleanupCheckinHandler(panel)
}

private class CodeCleanupCheckinHandler(private val panel: CheckinProjectPanel) : CheckinHandler(), CheckinMetaHandler {
  private val project = panel.project
  private val settings get() = VcsConfiguration.getInstance(project)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(panel, message("before.checkin.cleanup.code"), true, settings::CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT)

  override fun runCheckinHandlers(runnable: Runnable) {
    if (settings.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(project)) {
      val filesToProcess = filterOutGeneratedAndExcludedFiles(panel.virtualFiles, project)
      GlobalInspectionContextBase.modalCodeCleanup(project, AnalysisScope(project, filesToProcess), runnable)
    }
    else {
      runnable.run()
    }
  }
}