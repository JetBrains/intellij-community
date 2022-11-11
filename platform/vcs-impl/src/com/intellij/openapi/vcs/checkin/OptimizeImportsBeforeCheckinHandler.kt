// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent

class OptimizeOptionsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    OptimizeImportsBeforeCheckinHandler(panel)
}

class OptimizeImportsBeforeCheckinHandler(commitPanel: CheckinProjectPanel) : CodeProcessorCheckinHandler(commitPanel) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(commitPanel, VcsBundle.message("checkbox.checkin.options.optimize.imports"), true,
                        settings::OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT)

  override fun isEnabled(): Boolean = settings.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT

  override fun getProgressMessage(): String = VcsBundle.message("progress.text.optimizing.imports")

  override fun createCodeProcessor(): AbstractLayoutCodeProcessor =
    OptimizeImportsProcessor(project, getPsiFiles(project, commitPanel.virtualFiles), COMMAND_NAME, null)

  companion object {
    @JvmField
    @NlsSafe
    val COMMAND_NAME: String = CodeInsightBundle.message("process.optimize.imports.before.commit")
  }
}