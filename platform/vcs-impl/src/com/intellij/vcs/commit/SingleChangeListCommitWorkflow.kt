// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent

private val LOG = logger<SingleChangeListCommitWorkflow>()

private val CommitOptions.changeListSpecificOptions: Sequence<CheckinChangeListSpecificComponent>
  get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

internal fun CommitOptions.changeListChanged(changeList: LocalChangeList) = changeListSpecificOptions.forEach {
  it.onChangeListSelected(changeList)
}

internal fun CommitOptions.saveChangeListSpecificOptions() = changeListSpecificOptions.forEach { it.saveState() }

class SingleChangeListCommitWorkflow(
  project: Project,
  affectedVcses: Set<AbstractVcs>,
  val initiallyIncluded: Collection<*>,
  val initialChangeList: LocalChangeList?,
  executors: List<CommitExecutor>,
  override val isDefaultCommitEnabled: Boolean,
  initialCommitMessage: String?,
  private val resultHandler: CommitResultHandler?
) : CommitChangeListDialogWorkflow(project, initialCommitMessage) {

  init {
    updateVcses(affectedVcses)
    initCommitExecutors(executors)
  }

  override val isPartialCommitEnabled: Boolean =
    vcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || commitExecutors.any { it.supportsPartialCommit() })

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    if (sessionInfo.isVcsCommit) {
      doCommit(sessionInfo)
    }
    else {
      doCommitCustom(sessionInfo)
    }
  }

  override fun getBeforeCommitChecksChangelist(): LocalChangeList = commitState.changeList

  private fun doCommit(sessionInfo: CommitSessionInfo) {
    LOG.debug("Do actual commit")

    val committer = SingleChangeListCommitter.create(project, commitState, commitContext, DIALOG_TITLE)
    addCommonResultHandlers(sessionInfo, committer)
    if (resultHandler != null) {
      committer.addResultHandler(CommitResultHandlerNotifier(committer, resultHandler))
    }
    else {
      committer.addResultHandler(ShowNotificationCommitResultHandler(committer))
    }

    committer.runCommit(DIALOG_TITLE, false)
  }

  private fun doCommitCustom(sessionInfo: CommitSessionInfo) {
    sessionInfo as CommitSessionInfo.Custom

    val committer = CustomCommitter(project, sessionInfo.session, commitState.changes, commitState.commitMessage)
    addCommonResultHandlers(sessionInfo, committer)
    if (resultHandler != null) {
      committer.addResultHandler(CommitResultHandlerNotifier(committer, resultHandler))
    }

    committer.runCommit(sessionInfo.executor.actionText)
  }
}

abstract class CommitChangeListDialogWorkflow(
  project: Project,
  initialCommitMessage: String?
) : AbstractCommitWorkflow(project) {

  abstract val isPartialCommitEnabled: Boolean

  internal val commitMessagePolicy: SingleChangeListCommitMessagePolicy = SingleChangeListCommitMessagePolicy(project, initialCommitMessage)

  lateinit var commitState: ChangeListCommitState

  override fun addCommonResultHandlers(sessionInfo: CommitSessionInfo, committer: Committer) {
    super.addCommonResultHandlers(sessionInfo, committer)
    committer.addResultHandler(DefaultNameChangeListCleaner(project, commitState))
  }
}

private class DefaultNameChangeListCleaner(val project: Project, commitState: ChangeListCommitState) : CommitterResultHandler {
  private val isChangeListFullyIncluded = commitState.changeList.changes.size == commitState.changes.size
  private val isDefaultNameChangeList = commitState.changeList.hasDefaultName()
  private val listName = commitState.changeList.name

  override fun onSuccess() {
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(listName, "")
    }
  }
}