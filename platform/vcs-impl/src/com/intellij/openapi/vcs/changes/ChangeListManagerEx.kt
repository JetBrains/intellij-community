// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise

@ApiStatus.Experimental
@ApiStatus.NonExtendable
abstract class ChangeListManagerEx protected constructor() : ChangeListManager() {
  abstract fun isInUpdate(): Boolean

  abstract fun getUpdateException(): VcsException?

  abstract fun getAffectedLists(changes: Collection<Change>): Collection<LocalChangeList>

  abstract fun addChangeList(
    @NonNls name: String,
    @NonNls comment: String?,
    data: ChangeListData?,
  ): LocalChangeList

  abstract fun editChangeListData(@NonNls name: String, newData: ChangeListData?): Boolean

  /**
   * @param automatic true is changelist switch operation was not triggered by user (and, for example, will be reverted soon)
   * 4ex: This flag disables automatic empty changelist deletion.
   */
  abstract fun setDefaultChangeList(list: LocalChangeList, automatic: Boolean)

  /**
   * Add unversioned files into VCS under modal progress dialog
   *
   * @see com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
   */
  abstract fun addUnversionedFiles(list: LocalChangeList?, files: List<VirtualFile>)

  /**
   * Blocks modal dialogs that we don't want to popup during some process, for example, above the commit dialog.
   * They will be shown when notifications are unblocked.
   */
  @RequiresEdt
  abstract fun blockModalNotifications()

  @RequiresEdt
  abstract fun unblockModalNotifications()

  /**
   * Temporarily disable CLM update.
   * For example, to preserve FilePath->ChangeList mapping during "stash-do_smth-unstash" routine.
   */
  abstract fun freeze(@Nls reason: String)

  abstract fun unfreeze()

  /**
   * Wait until all current pending tasks are finished.
   *
   *
   * Do not execute this method while holding the read lock - it might be a long operation,
   * and CLM update can trigger synchronous VFS refresh that needs an EDT callback (causing a deadlock).
   *
   * @see ChangeListManager.invokeAfterUpdate
   */
  @RequiresBackgroundThread
  abstract fun waitForUpdate()

  /**
   * Wait until all current pending tasks are finished.
   *
   * @see waitForUpdate
   */
  abstract fun promiseWaitForUpdate(): Promise<*>

  companion object {
    @JvmStatic
    fun getInstanceEx(project: Project): ChangeListManagerEx = getInstance(project) as ChangeListManagerEx
  }
}