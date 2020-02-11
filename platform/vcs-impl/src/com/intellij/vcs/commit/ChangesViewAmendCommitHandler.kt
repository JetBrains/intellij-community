// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener

class ChangesViewAmendCommitHandler(private val workflowHandler: ChangesViewCommitWorkflowHandler) :
  AmendCommitHandlerImpl(workflowHandler) {

  init {
    project.messageBus.connect(workflowHandler).subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt { updateAmendCommitState() }
    })
  }

  override fun amendCommitModeToggled() {
    super.amendCommitModeToggled()

    workflowHandler.updateDefaultCommitActionEnabled()
    updateAmendCommitState()
  }

  internal fun isAmendWithoutChangesAllowed(): Boolean = isAmendCommitMode && getSingleRoot() != null

  private fun updateAmendCommitState() {
    commitContext.commitWithoutChangesRoots = if (isAmendCommitMode) listOfNotNull(getSingleRoot()) else emptyList()
  }
}