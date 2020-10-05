// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.NlsContexts

interface NonModalCommitWorkflowUi : CommitWorkflowUi, CommitActionsUi {
  fun showCommitOptions(options: CommitOptions, actionName: String, isFromToolbar: Boolean, dataContext: DataContext)
}

interface CommitActionsUi {
  var defaultCommitActionName: @NlsContexts.Button String
  var isDefaultCommitActionEnabled: Boolean

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun setCustomCommitActions(actions: List<AnAction>)
}