// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager.progress
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.NullableFunction
import com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress
import com.intellij.util.ui.ConfirmationDialog.requestForConfirmation
import org.jetbrains.annotations.CalledInAwt

class SingleChangeListCommitter(
  project: Project,
  private val changeList: LocalChangeList,
  changes: List<Change>,
  commitMessage: String,
  handlers: List<CheckinHandler>,
  additionalData: NullableFunction<Any, Any>,
  private val vcsToCommit: AbstractVcs<*>?,
  private val localHistoryActionName: String,
  private val isDefaultChangeListFullyIncluded: Boolean
) : AbstractCommitter(project, changes, commitMessage, handlers, additionalData) {

  private var myAction = LocalHistoryAction.NULL
  private var isSuccess = false

  override fun commit() {
    if (vcsToCommit != null && changes.isEmpty()) {
      commit(vcsToCommit, changes)
    }
    processChangesByVcs(project, changes, this::commit)
  }

  override fun afterCommit() {
    ChangeListManagerImpl.getInstanceImpl(project).showLocalChangesInvalidated()

    myAction = runReadAction { LocalHistory.getInstance().startAction(localHistoryActionName) }
  }

  override fun onSuccess() {
    isSuccess = true
  }

  override fun onFailure() {
    getApplication().invokeLater(Runnable {
      moveToFailedList(project, changeList, commitMessage, failedToCommitChanges,
                       message("commit.dialog.failed.commit.template", changeList.name))
    }, ModalityState.defaultModalityState(), project.disposed)
  }

  override fun onFinish() {
    refreshChanges()
    runOrInvokeLaterAboveProgress({ doPostRefresh() }, null, project)
  }

  private fun refreshChanges() {
    val toRefresh = mutableListOf<Change>()
    processChangesByVcs(project, changes) { vcs, changes ->
      val environment = vcs.checkinEnvironment
      if (environment != null && environment.isRefreshAfterCommitNeeded) {
        toRefresh.addAll(changes)
      }
    }

    if (!toRefresh.isEmpty()) {
      progress(message("commit.dialog.refresh.files"))
      RefreshVFsSynchronously.updateChanges(toRefresh)
    }
  }

  private fun doPostRefresh() {
    myAction.finish()
    if (!project.isDisposed) {
      // after vcs refresh is completed, outdated notifiers should be removed if some exists...
      VcsDirtyScopeManager.getInstance(project).filePathsDirty(pathsToRefresh, null)
      ChangeListManager.getInstance(project).invokeAfterUpdate(
        {
          if (isSuccess) {
            updateChangeListAfterRefresh()
          }

          val cache = CommittedChangesCache.getInstance(project)
          // in background since commit must have authorized
          cache.refreshAllCachesAsync(false, true)
          cache.refreshIncomingChangesAsync()
        }, InvokeAfterUpdateMode.SILENT, null, null)

      LocalHistory.getInstance().putSystemLabel(project, "$localHistoryActionName: $commitMessage")
    }
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
          MoveChangesToAnotherListAction.askAndMove(project, changes, emptyList<VirtualFile>())
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @CalledInAwt
    fun moveToFailedList(project: Project,
                         changeList: ChangeList,
                         commitMessage: String,
                         failedChanges: List<Change>,
                         newChangeListName: String) {
      // No need to move since we'll get exactly the same changelist.
      if (failedChanges.containsAll(changeList.changes)) return

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

      val failedList = changeListManager.addChangeList(failedListName, commitMessage)
      changeListManager.moveChangesTo(failedList, *failedChanges.toTypedArray())
    }
  }
}