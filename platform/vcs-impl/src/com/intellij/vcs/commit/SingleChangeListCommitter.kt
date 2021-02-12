// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction
import com.intellij.openapi.vcs.changes.ui.ChangelistMoveOfferDialog
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.ConfirmationDialog.requestForConfirmation
import org.jetbrains.annotations.ApiStatus

class ChangeListCommitState(val changeList: LocalChangeList, val changes: List<Change>, val commitMessage: String) {
  internal fun copy(commitMessage: String): ChangeListCommitState =
    if (this.commitMessage == commitMessage) this else ChangeListCommitState(changeList, changes, commitMessage)
}

open class SingleChangeListCommitter(
  project: Project,
  private val commitState: ChangeListCommitState,
  commitContext: CommitContext,
  localHistoryActionName: String,
  private val isDefaultChangeListFullyIncluded: Boolean
) : LocalChangesCommitter(project, commitState.changes, commitState.commitMessage, commitContext, localHistoryActionName) {

  @Deprecated("Use constructor without `vcsToCommit: AbstractVcs?` parameter")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  constructor(
    project: Project,
    commitState: ChangeListCommitState,
    commitContext: CommitContext,
    @Suppress("UNUSED_PARAMETER") vcsToCommit: AbstractVcs?, // external usages pass `null` here
    localHistoryActionName: String,
    isDefaultChangeListFullyIncluded: Boolean
  ) : this(project, commitState, commitContext, localHistoryActionName, isDefaultChangeListFullyIncluded)

  private val changeList get() = commitState.changeList

  override fun onFailure() {
    getApplication().invokeLater(Runnable {
      val failedCommitState = ChangeListCommitState(changeList, failedToCommitChanges, commitMessage)
      moveToFailedList(project, failedCommitState, message("commit.dialog.failed.commit.template", changeList.name))
    }, ModalityState.defaultModalityState(), project.disposed)
  }

  override fun afterRefreshChanges() {
    if (isSuccess) {
      updateChangeListAfterRefresh()
    }

    super.afterRefreshChanges()
  }

  private fun updateChangeListAfterRefresh() {
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    val listName = changeList.name
    val localList = changeListManager.findChangeList(listName) ?: return

    changeListManager.editChangeListData(listName, null)

    if (!localList.isDefault) {
      changeListManager.scheduleAutomaticEmptyChangeListDeletion(localList)
    }
    else {
      val changes = localList.changes
      if (configuration.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT && !changes.isEmpty() && isDefaultChangeListFullyIncluded) {
        val dialog = ChangelistMoveOfferDialog(configuration)
        if (dialog.showAndGet()) {
          MoveChangesToAnotherListAction.askAndMove(project, changes, emptyList())
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @RequiresEdt
    fun moveToFailedList(project: Project, commitState: ChangeListCommitState, newChangeListName: String) {
      // No need to move since we'll get exactly the same changelist.
      val failedChanges = commitState.changes
      if (failedChanges.containsAll(commitState.changeList.changes)) return

      val configuration = VcsConfiguration.getInstance(project)
      if (configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST != VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
        val option = object : VcsShowConfirmationOption {
          override fun getValue(): VcsShowConfirmationOption.Value = configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST

          override fun setValue(value: VcsShowConfirmationOption.Value) {
            configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST = value
          }

          override fun isPersistent(): Boolean = true
        }
        val result = requestForConfirmation(option, project, message("commit.failed.confirm.prompt"),
                                            message("commit.failed.confirm.title"), getQuestionIcon())
        if (!result) return
      }

      val changeListManager = ChangeListManager.getInstance(project)
      var index = 1
      var failedListName = newChangeListName
      while (changeListManager.findChangeList(failedListName) != null) {
        index++
        failedListName = "$newChangeListName ($index)"
      }

      val failedList = changeListManager.addChangeList(failedListName, commitState.commitMessage)
      changeListManager.moveChangesTo(failedList, *failedChanges.toTypedArray())
    }
  }
}