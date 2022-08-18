// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.*
import org.jetbrains.annotations.Nls

class ChangeListCommitState(val changeList: LocalChangeList, val changes: List<Change>, val commitMessage: String) {
  internal fun copy(commitMessage: String): ChangeListCommitState =
    if (this.commitMessage == commitMessage) this else ChangeListCommitState(changeList, changes, commitMessage)
}

open class SingleChangeListCommitter(
  project: Project,
  commitState: ChangeListCommitState,
  commitContext: CommitContext,
  localHistoryActionName: @Nls String,
) : LocalChangesCommitter(project, commitState, commitContext, localHistoryActionName) {

  @Deprecated("Use another constructor")
  constructor(project: Project,
              commitState: ChangeListCommitState,
              commitContext: CommitContext,
              localHistoryActionName: @Nls String,
              isDefaultChangeListFullyIncluded: Boolean) : this(project, commitState, commitContext, localHistoryActionName)

  private val changeList get() = commitState.changeList

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

    if (!localList.isDefault) {
      changeListManager.scheduleAutomaticEmptyChangeListDeletion(localList)
    }
  }
}