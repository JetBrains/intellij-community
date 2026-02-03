// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.CodeStyleBundle
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
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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
    ReformatCodeProcessor(project, getPsiFiles(project, files), CodeStyleBundle.message("process.reformat.code.before.commit"), null, true)
}