// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FileStatus.*
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import org.jetbrains.annotations.ApiStatus

/**
 * Checks if there are unresolved conflicts selected to commit.
 */
@ApiStatus.Internal
class UnresolvedMergeCheckFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    UnresolvedMergeCheckHandler(panel, commitContext)
}

private val MERGE_STATUSES = setOf(MERGE, MERGED_WITH_BOTH_CONFLICTS, MERGED_WITH_CONFLICTS, MERGED_WITH_PROPERTY_CONFLICTS)

private class UnresolvedMergeCheckHandler(
  private val panel: CheckinProjectPanel,
  private val commitContext: CommitContext
) : CheckinHandler(), CommitCheck, DumbAware {

  override fun isEnabled(): Boolean = true

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val providerResult = UnresolvedMergeCheckProvider.EP_NAME.extensions.asSequence()
      .mapNotNull { it.checkUnresolvedConflicts(panel, commitContext, commitInfo) }
      .firstOrNull()
    if (providerResult != null) return createProblemFor(providerResult)

    val hasUnresolvedConflicts = commitInfo.committedChanges.any { it.fileStatus in MERGE_STATUSES }
    if (!hasUnresolvedConflicts) return null

    val answer = showYesNoDialog(
      panel.component,
      VcsBundle.message("checkin.unresolved.merge.are.you.sure.you.want.to.commit.changes.with.unresolved.conflicts"),
      VcsBundle.message("checkin.unresolved.merge.unresolved.conflicts"), getWarningIcon()
    )
    val result = if (answer != Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
    return createProblemFor(result)
  }

  private fun createProblemFor(providerResult: ReturnResult): CommitProblem? {
    if (providerResult == ReturnResult.COMMIT) return null
    return MergeConflictsCommitProblem(providerResult)
  }
}

private class MergeConflictsCommitProblem(val result: CheckinHandler.ReturnResult) : CommitProblem {
  override val text: String get() = VcsBundle.message("before.checkin.error.unresolved.merge.conflicts")

  override fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult {
    return result // dialog was already shown
  }
}

