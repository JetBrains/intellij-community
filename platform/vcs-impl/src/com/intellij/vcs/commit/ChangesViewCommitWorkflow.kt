// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.LocalChangeList

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

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    if (sessionInfo.isVcsCommit) {
      doCommit()
    }
    else {
      doCommitCustom(sessionInfo)
    }
  }

  override fun getBeforeCommitChecksChangelist(): LocalChangeList = commitState.changeList

  private fun doCommit() {
    LOG.debug("Do actual commit")

    with(LocalChangesCommitter(project, commitState, commitContext)) {
      addResultHandler(CheckinHandlersNotifier(this, commitHandlers))
      addResultHandler(getCommitEventDispatcher(this))
      addResultHandler(ShowNotificationCommitResultHandler(this))
      addResultHandler(object : CommitterResultHandler {
        override fun onAfterRefresh() {
          endExecution()
        }
      })

      runCommit(VcsBundle.message("commit.changes"), false)
    }
  }

  private fun doCommitCustom(sessionInfo: CommitSessionInfo) {
    sessionInfo as CommitSessionInfo.Custom

    with(CustomCommitter(project, sessionInfo.session, commitState.changes, commitState.commitMessage)) {
      addResultHandler(CheckinHandlersNotifier(this, commitHandlers))
      addResultHandler(getCommitCustomEventDispatcher(this))
      addResultHandler(getEndExecutionHandler())

      runCommit(sessionInfo.executor.actionText)
    }
  }
}