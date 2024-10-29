// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandlerUtil.getPsiFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.formatter.FormatterUtil.getReformatBeforeCommitCommandName
import org.jetbrains.annotations.ApiStatus

class ReformatCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    ReformatBeforeCheckinHandler(panel.project)
}

@ApiStatus.Internal
class ReformatBeforeCheckinHandler(project: Project) : CodeProcessorCheckinHandler(project) {
  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
    BooleanCommitOption.create(project, this, disableWhenDumb = true,
                               message("checkbox.checkin.options.reformat.code"),
                               settings::REFORMAT_BEFORE_PROJECT_COMMIT)

  override fun isEnabled(): Boolean = settings.REFORMAT_BEFORE_PROJECT_COMMIT

  override fun getProgressMessage(): String = message("progress.text.reformatting.code")

  override fun createCodeProcessor(files: List<VirtualFile>): AbstractLayoutCodeProcessor =
    ReformatCodeProcessor(project, getPsiFiles(project, files), getReformatBeforeCommitCommandName(), null, true)
}