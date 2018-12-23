// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.getExecutorPresentableText
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.NullableFunction

private val LOG = logger<DialogCommitWorkflow>()
private val TASK_TITLE = message("commit.dialog.title")

open class DialogCommitWorkflow(val project: Project,
                                val initiallyIncluded: Collection<*>,
                                val initialChangeList: LocalChangeList? = null,
                                val executors: List<CommitExecutor> = emptyList(),
                                val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
                                val vcsToCommit: AbstractVcs<*>? = null,
                                val affectedVcses: Set<AbstractVcs<*>> = if (vcsToCommit != null) setOf(vcsToCommit) else emptySet(),
                                private val isDefaultChangeListFullyIncluded: Boolean = true,
                                val initialCommitMessage: String? = null,
                                val resultHandler: CommitResultHandler? = null) {
  val isPartialCommitEnabled: Boolean = affectedVcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || executors.any { it.supportsPartialCommit() })

  fun showDialog(): Boolean {
    val dialog = CommitChangeListDialog(this)
    return dialog.showAndGet()
  }

  protected open val isAlien: Boolean get() = false

  protected open fun prepareCommit(unversionedFiles: List<VirtualFile>, browser: CommitDialogChangesBrowser): Boolean =
    ScheduleForAdditionAction.addUnversioned(project, unversionedFiles, browser)

  protected open fun doRunBeforeCommitChecks(changeList: LocalChangeList, checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, changeList, checks)

  protected open fun canExecute(executor: CommitExecutor, changes: Collection<Change>): Boolean {
    if (!executor.supportsPartialCommit()) {
      val hasPartialChanges = changes.any { getPartialTracker(project, it)?.hasPartialChangesToCommit() ?: false }
      if (hasPartialChanges) {
        return Messages.YES == showYesNoDialog(
          project, message("commit.dialog.partial.commit.warning.body", getExecutorPresentableText(executor)),
          message("commit.dialog.partial.commit.warning.title"), executor.actionText, getCancelButtonText(), getWarningIcon())
      }
    }
    return true
  }

  protected fun doCommit(changeList: LocalChangeList,
                         changes: List<Change>,
                         commitMessage: String,
                         handlers: List<CheckinHandler>,
                         additionalData: NullableFunction<Any, Any>) {
    LOG.debug("Do actual commit")
    val helper = CommitHelper(project, changeList, changes, TASK_TITLE, commitMessage, handlers, isDefaultChangeListFullyIncluded, false,
                              additionalData, resultHandler, isAlien, vcsToCommit)
    helper.doCommit()
  }
}