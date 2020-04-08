// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.vcs.log.VcsUser
import java.util.*

interface ChangesViewCommitWorkflowUi : CommitWorkflowUi {
  val isActive: Boolean
  fun deactivate(isRestoreState: Boolean)

  var isDefaultCommitActionEnabled: Boolean
  fun setCustomCommitActions(actions: List<AnAction>)

  var commitAuthor: VcsUser?
  fun addCommitAuthorListener(listener: CommitAuthorListener, parent: Disposable)

  var editedCommit: EditedCommitDetails?

  var inclusionModel: InclusionModel?

  fun expand(item: Any)
  fun select(item: Any)
  fun selectFirst(items: Collection<Any>)

  fun showCommitOptions(options: CommitOptions, actionName: String, isFromToolbar: Boolean, dataContext: DataContext)

  fun setCompletionContext(changeLists: List<LocalChangeList>)
}

interface CommitAuthorListener : EventListener {
  fun commitAuthorChanged()
}