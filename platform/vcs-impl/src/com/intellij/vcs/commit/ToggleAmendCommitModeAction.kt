// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsDataKeys

class ToggleAmendCommitModeAction : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val amendCommitHandler = getAmendCommitHandler(e)
    with(e.presentation) {
      isVisible = amendCommitHandler?.isAmendCommitModeSupported() == true
      isEnabled = isVisible && amendCommitHandler?.isAmendCommitModeTogglingEnabled == true
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = getAmendCommitHandler(e)?.isAmendCommitMode == true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getAmendCommitHandler(e)!!.isAmendCommitMode = state
  }

  private fun getAmendCommitHandler(e: AnActionEvent) = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)?.amendCommitHandler
}