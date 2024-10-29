// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

interface NonModalCommitWorkflowUi : CommitWorkflowUi, CommitActionsUi, CommitAuthorTracker {
  val commitProgressUi: CommitProgressUi

  var editedCommit: EditedCommitPresentation?

  fun showCommitOptions(options: CommitOptions, actionName: @Nls String, isFromToolbar: Boolean, dataContext: DataContext)
}

@ApiStatus.Internal
interface CommitActionsUi {
  var defaultCommitActionName: @NlsContexts.Button String
  var isDefaultCommitActionEnabled: Boolean

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun setPrimaryCommitActions(actions: List<AnAction>)
  fun setCustomCommitActions(actions: List<AnAction>)
}

@ApiStatus.Experimental
interface CommitProgressUi {
  var isEmptyMessage: Boolean
  var isEmptyChanges: Boolean

  var isDumbMode: Boolean

  suspend fun <T> runWithProgress(isOnlyRunCommitChecks: Boolean, action: suspend CoroutineScope.() -> T): T

  fun addCommitCheckFailure(failure: CommitCheckFailure)

  fun clearCommitCheckFailures()
  fun getCommitCheckFailures(): List<CommitCheckFailure>
}