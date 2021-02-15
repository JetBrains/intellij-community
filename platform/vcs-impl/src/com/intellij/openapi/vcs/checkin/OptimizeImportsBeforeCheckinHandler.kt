// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.checkin.OptimizeImportsBeforeCheckinHandler.Companion.COMMAND_NAME
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.vcs.commit.isBackgroundCommitChecks
import com.intellij.vcs.commit.isNonModalCommit

open class OptimizeOptionsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    if (isBackgroundCommitChecks() && panel.isNonModalCommit) BackgroundOptimizeImportsCheckinHandler(panel)
    else OptimizeImportsBeforeCheckinHandler(panel.project, panel)
}

open class OptimizeImportsBeforeCheckinHandler(
  @JvmField protected val myProject: Project,
  private val panel: CheckinProjectPanel
) : CheckinHandler(),
    CheckinMetaHandler {

  private val settings get() = VcsConfiguration.getInstance(myProject)

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(panel, VcsBundle.message("checkbox.checkin.options.optimize.imports"), true,
                        settings::OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT)

  override fun runCheckinHandlers(runnable: Runnable) {
    val saveAndContinue = {
      FileDocumentManager.getInstance().saveAllDocuments()
      runnable.run()
    }

    if (settings.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      OptimizeImportsProcessor(myProject, getPsiFiles(myProject, panel.virtualFiles), COMMAND_NAME, saveAndContinue).run()
    }
    else {
      runnable.run()
    }
  }

  companion object {
    @JvmField
    @NlsSafe
    val COMMAND_NAME: String = CodeInsightBundle.message("process.optimize.imports.before.commit")
  }
}

private class BackgroundOptimizeImportsCheckinHandler(commitPanel: CheckinProjectPanel) : CodeProcessorCheckinHandler(commitPanel) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    OptimizeImportsBeforeCheckinHandler(project, commitPanel).beforeCheckinConfigurationPanel

  override fun isEnabled(): Boolean = settings.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT

  override fun createCodeProcessor(): AbstractLayoutCodeProcessor =
    OptimizeImportsProcessor(project, getPsiFiles(project, commitPanel.virtualFiles), COMMAND_NAME, null)
}