// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.*
import org.jetbrains.annotations.Nls

class ChangeListCommitState(val changeList: LocalChangeList, val changes: List<Change>, val commitMessage: String) {
  internal fun copy(commitMessage: String): ChangeListCommitState =
    if (this.commitMessage == commitMessage) this else ChangeListCommitState(changeList, changes, commitMessage)
}

class SingleChangeListCommitter
@Deprecated("Prefer using SingleChangeListCommitter.create",
            replaceWith = ReplaceWith("SingleChangeListCommitter.create(project, commitState, commitContext, localHistoryActionName)"))
constructor(
  project: Project,
  commitState: ChangeListCommitState,
  commitContext: CommitContext,
  localHistoryActionName: @Nls String,
  isDefaultChangeListFullyIncluded: Boolean
) : LocalChangesCommitter(project, commitState, commitContext, localHistoryActionName) {

  @Deprecated("Prefer using CommitterResultHandler")
  fun addResultHandler(resultHandler: CommitResultHandler) {
    addResultHandler(CommitResultHandlerNotifier(this, resultHandler))
  }

  init {
    addResultHandler(EmptyChangeListDeleter(this))
  }

  companion object {
    @JvmStatic
    fun create(project: Project,
               commitState: ChangeListCommitState,
               commitContext: CommitContext,
               localHistoryActionName: @Nls String): LocalChangesCommitter {
      val committer = LocalChangesCommitter(project, commitState, commitContext, localHistoryActionName)
      committer.addResultHandler(EmptyChangeListDeleter(committer))
      return committer
    }
  }
}

private class EmptyChangeListDeleter(val committer: LocalChangesCommitter) : CommitterResultHandler {
  override fun onAfterRefresh() {
    if (committer.isSuccess) {
      val changeListManager = ChangeListManagerImpl.getInstanceImpl(committer.project)
      val listName = committer.commitState.changeList.name
      val localList = changeListManager.findChangeList(listName) ?: return

      if (!localList.isDefault) {
        changeListManager.scheduleAutomaticEmptyChangeListDeletion(localList)
      }
    }
  }
}
