// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.CommitContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultipleChangeListsCheckFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    MultipleChangeListsCheckHandler(panel, commitContext)
}

private class MultipleChangeListsCheckHandler(
  private val panel: CheckinProjectPanel,
  private val commitContext: CommitContext
) : CheckinHandler(), CommitCheck, DumbAware {

  override fun isEnabled(): Boolean = true

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val changes = commitInfo.committedChanges
    val changeListIds = changes.mapNotNullTo(HashSet()) { (it as? ChangeListChange)?.changeListId }
    if (changeListIds.size > 1) {
      return MultipleChangeListsCommitProblem()
    }

    return null
  }
}

private class MultipleChangeListsCommitProblem() : CommitProblem {
  override val text: String get() = VcsBundle.message("before.checkin.error.multiple.changelists.selected")
}
