// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.psi.formatter.FormatterUtil.REFORMAT_BEFORE_COMMIT_COMMAND_NAME

open class ReformatCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    ReformatBeforeCheckinHandler(panel.project, panel)
}

open class ReformatBeforeCheckinHandler(
  @JvmField protected val myProject: Project,
  private val panel: CheckinProjectPanel
) : CheckinHandler(),
    CheckinMetaHandler {

  private val settings get() = VcsConfiguration.getInstance(myProject)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(panel, message("checkbox.checkin.options.reformat.code"), true, settings::REFORMAT_BEFORE_PROJECT_COMMIT)

  override fun runCheckinHandlers(runnable: Runnable) {
    val saveAndContinue = {
      FileDocumentManager.getInstance().saveAllDocuments()
      runnable.run()
    }

    if (settings.REFORMAT_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      ReformatCodeProcessor(myProject, getPsiFiles(myProject, panel.virtualFiles), REFORMAT_BEFORE_COMMIT_COMMAND_NAME, saveAndContinue,
                            true).run()
    }
    else {
      saveAndContinue() // TODO just runnable.run()?
    }
  }
}
