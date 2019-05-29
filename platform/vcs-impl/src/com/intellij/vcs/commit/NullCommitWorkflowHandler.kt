// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.vcs.commit.CommitWorkflowHandler

object NullCommitWorkflowHandler : CommitWorkflowHandler {
  @Suppress("UNUSED_PARAMETER")
  override var isAmendCommitMode: Boolean
    get() = false
    set(value) = Unit

  override fun getExecutor(executorId: String): CommitExecutor? = null
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = false
  override fun execute(executor: CommitExecutor) = Unit
}