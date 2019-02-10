// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.CommitExecutor

class SingleChangeListCommitWorkflowHandler(private val ui: CommitChangeListDialog) : CommitExecutorListener, Disposable {
  init {
    ui.addExecutorListener(this, this)
  }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { ui.execute(it) } ?: ui.executeDefaultCommitSession(null)

  override fun dispose() = Unit
}