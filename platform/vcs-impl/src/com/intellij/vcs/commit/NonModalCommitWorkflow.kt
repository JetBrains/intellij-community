// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  internal fun asyncSession(scope: CoroutineScope,
                            sessionInfo: CommitSessionInfo,
                            commitChecks: suspend () -> CommitChecksResult) {
    check(isExecuting) { "Commit session has already finished" }
    scope.launch {
      try {
        fireBeforeCommitChecksStarted(sessionInfo)
        val result = commitChecks()
        fireBeforeCommitChecksEnded(sessionInfo, result)

        if (result.shouldCommit) {
          performCommit(sessionInfo)
        }
        else {
          endExecution()
        }
      }
      catch (e: Throwable) {
        endExecution()
        throw e
      }
    }
  }
}
