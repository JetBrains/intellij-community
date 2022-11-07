// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile

class OptimizeOptionsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    OptimizeImportsBeforeCheckinHandler(panel.project)
}

class OptimizeImportsBeforeCheckinHandler(project: Project) : CodeProcessorCheckinHandler(project) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(project, VcsBundle.message("checkbox.checkin.options.optimize.imports"), true,
                        settings::OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT)
      .withCheckinHandler(this)

  override fun isEnabled(): Boolean = settings.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT

  override fun getProgressMessage(): String = VcsBundle.message("progress.text.optimizing.imports")

  override fun createCodeProcessor(files: List<VirtualFile>): AbstractLayoutCodeProcessor =
    OptimizeImportsProcessor(project, getPsiFiles(project, files), COMMAND_NAME, null)

  companion object {
    @JvmField
    @NlsSafe
    val COMMAND_NAME: String = CodeInsightBundle.message("process.optimize.imports.before.commit")
  }
}