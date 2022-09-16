// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.changes.CommitExecutor

interface CommitWorkflowHandler {
  val amendCommitHandler: AmendCommitHandler

  fun getExecutor(executorId: String): CommitExecutor?
  fun isExecutorEnabled(executor: CommitExecutor): Boolean
  fun execute(executor: CommitExecutor)
}

sealed class CommitChecksResult {
  class Passed(val toCommit: Boolean) : CommitChecksResult()
  class Failed(val toCloseWindow: Boolean = false) : CommitChecksResult()
  object Cancelled : CommitChecksResult()
  object ExecutionError : CommitChecksResult()

  val shouldCommit: Boolean get() = this is Passed && toCommit
  val shouldCloseWindow: Boolean get() = this is Failed && toCloseWindow
}
