// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.PartialChangesUtil

private val LOG = logger<ChangesViewCommitWorkflow>()

class ChangesViewCommitWorkflow(project: Project) : NonModalCommitWorkflow(project) {
  private val vcsManager get() = ProjectLevelVcsManager.getInstance(project)
  private val changeListManager get() = ChangeListManagerEx.getInstanceEx(project)

  override val isDefaultCommitEnabled: Boolean get() = true

  internal lateinit var commitState: ChangeListCommitState

  init {
    updateVcses(vcsManager.allActiveVcss.toSet())
  }

  internal fun getAffectedChangeList(changes: Collection<Change>): LocalChangeList =
    changes.firstOrNull()?.let { changeListManager.getChangeList(it) } ?: changeListManager.defaultChangeList

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommit()
  }

  override fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean =
    executeCustom(executor, session, commitState.changes, commitState.commitMessage)

  override fun processExecuteCustomChecksResult(executor: CommitExecutor, session: CommitSession, result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommitCustom(executor, session)
  }

  override fun doRunBeforeCommitChecks(checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, commitState.changeList, checks)

  private fun doCommit() {
    LOG.debug("Do actual commit")

    with(object : LocalChangesCommitter(project, commitState.changes, commitState.commitMessage, commitContext) {
      override fun afterRefreshChanges() = endExecution {
        if (isSuccess) clearChangeListData()
        super.afterRefreshChanges()
      }
    }) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(ShowNotificationCommitResultHandler(this))

      runCommit(VcsBundle.message("commit.changes"), false)
    }
  }

  private fun doCommitCustom(executor: CommitExecutor, session: CommitSession) =
    with(CustomCommitter(project, session, commitState.changes, commitState.commitMessage)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitCustomEventDispatcher())
      addResultHandler(getEndExecutionHandler())

      runCommit(executor.actionText)
    }

  private fun clearChangeListData() {
    changeListManager.editChangeListData(commitState.changeList.name, null)
  }
}