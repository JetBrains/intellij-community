// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FileStatus.*
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.util.PairConsumer

/**
 * Checks if there are unresolved conflicts selected to commit.
 */
class UnresolvedMergeCheckFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    UnresolvedMergeCheckHandler(panel, commitContext)
}

private val MERGE_STATUSES = setOf(MERGE, MERGED_WITH_BOTH_CONFLICTS, MERGED_WITH_CONFLICTS, MERGED_WITH_PROPERTY_CONFLICTS)

private class UnresolvedMergeCheckHandler(
  private val panel: CheckinProjectPanel,
  private val commitContext: CommitContext
) : CheckinHandler() {

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    val providerResult = UnresolvedMergeCheckProvider.EP_NAME.extensions.asSequence()
      .mapNotNull { it.checkUnresolvedConflicts(panel, commitContext, executor) }
      .firstOrNull()
    return providerResult ?: performDefaultCheck()
  }

  private fun performDefaultCheck(): ReturnResult = if (panel.hasUnresolvedConflicts()) askUser() else ReturnResult.COMMIT

  private fun askUser(): ReturnResult {
    val answer = showYesNoDialog(panel.component, VcsBundle.message(
      "checkin.unresolved.merge.are.you.sure.you.want.to.commit.changes.with.unresolved.conflicts"),
                                 VcsBundle.message("checkin.unresolved.merge.unresolved.conflicts"), getWarningIcon())
    return if (answer != Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
  }

  private fun CheckinProjectPanel.hasUnresolvedConflicts() = selectedChanges.any { it.fileStatus in MERGE_STATUSES }
}
