// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val LOG = logger<NonModalCommitWorkflow>()

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

  suspend fun runBackgroundBeforeCommitChecks(commitInfo: DynamicCommitInfo): CommitProblem? {
    return PartialChangesUtil.underChangeList(project, getBeforeCommitChecksChangelist()) {
      runCommitHandlers(commitInfo)
    }
  }

  private suspend fun runCommitHandlers(commitInfo: DynamicCommitInfo): CommitProblem? {
    try {
      val handlers = commitHandlers
      val commitChecks = handlers
        .map { it.asCommitCheck(commitInfo) }
        .groupBy { it.getExecutionOrder() }

      runCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.EARLY])?.let { return it }

      runMetaHandlers(handlers.filterIsInstance<CheckinMetaHandler>())

      runCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.MODIFICATION])?.let { return it }
      FileDocumentManager.getInstance().saveAllDocuments()

      runCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.LATE])?.let { return it }

      return null // checks passed
    }
    catch (ce: CancellationException) {
      // Do not report error on cancellation
      throw ce
    }
    catch (e: Throwable) {
      LOG.warn(Throwable(e))
      return CommitProblem.createError(e)
    }
  }

  private suspend fun runCommitChecks(commitInfo: DynamicCommitInfo, commitChecks: List<CommitCheck>?): CommitProblem? {
    for (commitCheck in commitChecks.orEmpty()) {
      val problem = runCommitCheck(project, commitCheck, commitInfo)
      if (problem != null) return problem
    }
    return null
  }
}
