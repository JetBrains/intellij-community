// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

interface NonModalCommitWorkflowUi : CommitWorkflowUi, CommitActionsUi, CommitAuthorTracker {
  val commitProgressUi: CommitProgressUi

  var editedCommit: EditedCommitDetails?

  fun showCommitOptions(options: CommitOptions, actionName: String, isFromToolbar: Boolean, dataContext: DataContext)
}

interface CommitActionsUi {
  var defaultCommitActionName: @NlsContexts.Button String
  var isDefaultCommitActionEnabled: Boolean

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun setCustomCommitActions(actions: List<AnAction>)
}

@ApiStatus.Experimental
interface CommitProgressUi {
  var isEmptyMessage: Boolean
  var isEmptyChanges: Boolean

  var isDumbMode: Boolean

  fun startProgress()
  fun addCommitCheckFailure(@Nls(capitalization = Sentence) text: String, detailsViewer: () -> Unit)
  fun clearCommitCheckFailures()
  fun endProgress()
}