// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogUiEx

data class CommitEditingActionContext(
  val selection: VcsLogCommitSelection,
  val logData: VcsLogData,
  val logUiEx: VcsLogUiEx? = null,
  val selectedChanges: List<Change> = emptyList(),
)

internal fun DataContext.getCommitEditingData(): CommitEditingActionContext? {
  val selection = getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return null
  val logDataProvider = getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as? VcsLogData ?: return null
  val logUiEx = getData(VcsLogInternalDataKeys.LOG_UI_EX)
  val selectedChanges = getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS)?.toList() ?: emptyList()

  return CommitEditingActionContext(selection = selection, logData = logDataProvider, logUiEx = logUiEx, selectedChanges = selectedChanges)
}