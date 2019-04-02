// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.changes.CommitWorkflowUi

interface ChangesViewCommitWorkflowUi : CommitWorkflowUi {
  var isDefaultCommitActionEnabled: Boolean

  fun showCommitOptions(options: CommitOptions, isFromToolbar: Boolean, dataContext: DataContext)
}