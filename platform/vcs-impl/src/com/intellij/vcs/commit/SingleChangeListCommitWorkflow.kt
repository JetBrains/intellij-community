// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.util.ui.UIUtil.removeMnemonic
import com.intellij.vcs.commit.SingleChangeListCommitter.Companion.moveToFailedList

private val LOG = logger<SingleChangeListCommitWorkflow>()

internal val CommitOptions.changeListSpecificOptions: Sequence<CheckinChangeListSpecificComponent>
  get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

internal fun CommitOptions.changeListChanged(changeList: LocalChangeList) = changeListSpecificOptions.forEach {
  it.onChangeListSelected(changeList)
}

internal fun CommitOptions.saveChangeListSpecificOptions() = changeListSpecificOptions.forEach { it.saveState() }

internal fun removeEllipsisSuffix(s: String) = s.removeSuffix("...").removeSuffix("\u2026")
internal fun CommitExecutor.getPresentableText() = removeEllipsisSuffix(removeMnemonic(actionText))

open class SingleChangeListCommitWorkflow(
  project: Project,
  val initiallyIncluded: Collection<*>,
  val initialChangeList: LocalChangeList? = null,
  executors: List<CommitExecutor> = emptyList(),
  final override val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
  val vcsToCommit: AbstractVcs? = null,
  affectedVcses: Set<AbstractVcs> = if (vcsToCommit != null) setOf(vcsToCommit) else emptySet(),
  private val isDefaultChangeListFullyIncluded: Boolean = true,
  val initialCommitMessage: String? = null,
  private val resultHandler: CommitResultHandler? = null
) : AbstractCommitWorkflow(project) {

  init {
    updateVcses(affectedVcses)
    initCommitExecutors(executors)
  }

  val isPartialCommitEnabled: Boolean =
    vcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || commitExecutors.any { it.supportsPartialCommit() })

  internal val commitMessagePolicy: SingleChangeListCommitMessagePolicy = SingleChangeListCommitMessagePolicy(project, initialCommitMessage)

  internal lateinit var commitState: ChangeListCommitState

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) = when (result) {
    CheckinHandler.ReturnResult.COMMIT -> DefaultNameChangeListCleaner(project, commitState).use { doCommit(commitState) }
    CheckinHandler.ReturnResult.CLOSE_WINDOW ->
      moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
    CheckinHandler.ReturnResult.CANCEL -> Unit
  }

  override fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean =
    executeCustom(executor, session, commitState.changes, commitState.commitMessage)

  override fun processExecuteCustomChecksResult(executor: CommitExecutor, session: CommitSession, result: CheckinHandler.ReturnResult) =
    when (result) {
      CheckinHandler.ReturnResult.COMMIT -> doCommitCustom(executor, session)
      CheckinHandler.ReturnResult.CLOSE_WINDOW ->
        moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
      CheckinHandler.ReturnResult.CANCEL -> Unit
    }

  override fun doRunBeforeCommitChecks(checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, commitState.changeList, checks)

  protected open fun doCommit(commitState: ChangeListCommitState) {
    LOG.debug("Do actual commit")

    with(object : SingleChangeListCommitter(project, commitState, commitContext, vcsToCommit, DIALOG_TITLE,
                                            isDefaultChangeListFullyIncluded) {
      override fun afterRefreshChanges() = endExecution { super.afterRefreshChanges() }
    }) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(resultHandler ?: ShowNotificationCommitResultHandler(this))

      runCommit(DIALOG_TITLE, false)
    }
  }

  private fun doCommitCustom(executor: CommitExecutor, session: CommitSession) {
    val cleaner = DefaultNameChangeListCleaner(project, commitState)

    with(CustomCommitter(project, session, commitState.changes, commitState.commitMessage)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(CommitResultHandler { cleaner.clean() })
      addResultHandler(getCommitCustomEventDispatcher())
      resultHandler?.let { addResultHandler(it) }
      addResultHandler(getEndExecutionHandler())

      runCommit(executor.actionText)
    }
  }
}

private class DefaultNameChangeListCleaner(val project: Project, commitState: ChangeListCommitState) {
  private val isChangeListFullyIncluded = commitState.changeList.changes.size == commitState.changes.size
  private val isDefaultNameChangeList = commitState.changeList.hasDefaultName()

  fun use(block: () -> Unit) {
    block()
    clean()
  }

  fun clean() {
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(LocalChangeList.DEFAULT_NAME, "")
    }
  }
}