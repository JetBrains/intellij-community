// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
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
import com.intellij.psi.formatter.FormatterUtil.getReformatBeforeCommitCommandName
import com.intellij.vcs.commit.isBackgroundCommitChecks
import com.intellij.vcs.commit.isNonModalCommit

open class ReformatCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    if (isBackgroundCommitChecks() && panel.isNonModalCommit) BackgroundReformatCheckinHandler(panel)
    else ReformatBeforeCheckinHandler(panel.project, panel)
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
      ReformatCodeProcessor(myProject, getPsiFiles(myProject, panel.virtualFiles), getReformatBeforeCommitCommandName(), saveAndContinue,
                            true).run()
    }
    else {
      saveAndContinue() // TODO just runnable.run()?
    }
  }
}

private class BackgroundReformatCheckinHandler(commitPanel: CheckinProjectPanel) : CodeProcessorCheckinHandler(commitPanel) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    ReformatBeforeCheckinHandler(project, commitPanel).beforeCheckinConfigurationPanel

  override fun isEnabled(): Boolean = settings.REFORMAT_BEFORE_PROJECT_COMMIT

  override fun createCodeProcessor(): AbstractLayoutCodeProcessor =
    ReformatCodeProcessor(project, getPsiFiles(project, commitPanel.virtualFiles), getReformatBeforeCommitCommandName(), null, true)
}