// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.util.ui.UIUtil.removeMnemonic
import org.jetbrains.annotations.Nls

private val LOG = logger<SingleChangeListCommitWorkflow>()

private val CommitOptions.changeListSpecificOptions: Sequence<CheckinChangeListSpecificComponent>
  get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

internal fun CommitOptions.changeListChanged(changeList: LocalChangeList) = changeListSpecificOptions.forEach {
  it.onChangeListSelected(changeList)
}

internal fun CommitOptions.saveChangeListSpecificOptions() = changeListSpecificOptions.forEach { it.saveState() }

@Nls
internal fun String.removeEllipsisSuffix() = StringUtil.removeEllipsisSuffix(this)

@Nls
internal fun CommitExecutor.getPresentableText() = removeMnemonic(actionText).removeEllipsisSuffix()

open class SingleChangeListCommitWorkflow(
  project: Project,
  affectedVcses: Set<AbstractVcs>,
  val initiallyIncluded: Collection<*>,
  val initialChangeList: LocalChangeList? = null,
  executors: List<CommitExecutor> = emptyList(),
  final override val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
  val initialCommitMessage: String? = null,
  private val resultHandler: CommitResultHandler? = null
) : CommitChangeListDialogWorkflow(project, initialCommitMessage) {

  init {
    updateVcses(affectedVcses)
    initCommitExecutors(executors)
  }

  override val isPartialCommitEnabled: Boolean =
    vcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || commitExecutors.any { it.supportsPartialCommit() })

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    if (sessionInfo.isVcsCommit) {
      DefaultNameChangeListCleaner(project, commitState).use { doCommit(commitState) }
    }
    else {
      doCommitCustom(sessionInfo)
    }
  }

  override fun getBeforeCommitChecksChangelist(): LocalChangeList? = commitState.changeList

  protected open fun doCommit(commitState: ChangeListCommitState) {
    LOG.debug("Do actual commit")

    with(object : SingleChangeListCommitter(project, commitState, commitContext, DIALOG_TITLE) {
      override fun afterRefreshChanges() = endExecution { super.afterRefreshChanges() }
    }) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(resultHandler ?: ShowNotificationCommitResultHandler(this))

      runCommit(DIALOG_TITLE, false)
    }
  }

  private fun doCommitCustom(sessionInfo: CommitSessionInfo) {
    sessionInfo as CommitSessionInfo.Custom
    val cleaner = DefaultNameChangeListCleaner(project, commitState)

    with(CustomCommitter(project, sessionInfo.session, commitState.changes, commitState.commitMessage)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(CommitResultHandler { cleaner.clean() })
      addResultHandler(getCommitCustomEventDispatcher())
      resultHandler?.let { addResultHandler(it) }
      addResultHandler(getEndExecutionHandler())

      runCommit(sessionInfo.executor.actionText)
    }
  }
}

abstract class CommitChangeListDialogWorkflow(
  project: Project,
  initialCommitMessage: String?
) : AbstractCommitWorkflow(project) {

  abstract val isPartialCommitEnabled: Boolean

  internal val commitMessagePolicy: SingleChangeListCommitMessagePolicy = SingleChangeListCommitMessagePolicy(project, initialCommitMessage)

  lateinit var commitState: ChangeListCommitState
}

private class DefaultNameChangeListCleaner(val project: Project, commitState: ChangeListCommitState) {
  private val isChangeListFullyIncluded = commitState.changeList.changes.size == commitState.changes.size
  private val isDefaultNameChangeList = commitState.changeList.hasDefaultName()
  private val listName = commitState.changeList.name

  fun use(block: () -> Unit) {
    block()
    clean()
  }

  fun clean() {
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(listName, "")
    }
  }
}