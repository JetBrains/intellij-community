// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.changes.CommitExecutor

interface CommitWorkflowHandler {
  val amendCommitHandler: AmendCommitHandler

  fun getExecutor(executorId: String): CommitExecutor?
  fun isExecutorEnabled(executor: CommitExecutor): Boolean
  fun execute(executor: CommitExecutor)
}