// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.RearrangeCodeProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile

class RearrangeCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    RearrangeBeforeCheckinHandler(panel.project)
}

class RearrangeBeforeCheckinHandler(project: Project) : CodeProcessorCheckinHandler(project) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption(project, VcsBundle.message("checkbox.checkin.options.rearrange.code"), true,
                        settings::REARRANGE_BEFORE_PROJECT_COMMIT)

  override fun isEnabled(): Boolean = settings.REARRANGE_BEFORE_PROJECT_COMMIT

  override fun getProgressMessage(): String = VcsBundle.message("progress.text.rearranging.code")

  override fun createCodeProcessor(files: List<VirtualFile>): AbstractLayoutCodeProcessor =
    RearrangeCodeProcessor(project, getPsiFiles(project, files), COMMAND_NAME, null, true)

  companion object {
    @JvmField
    @NlsSafe
    val COMMAND_NAME: String = CodeInsightBundle.message("process.rearrange.code.before.commit")
  }
}