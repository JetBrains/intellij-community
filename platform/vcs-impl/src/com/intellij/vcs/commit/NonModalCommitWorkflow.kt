// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val LOG = logger<NonModalCommitWorkflow>()

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  internal fun asyncSession(scope: CoroutineScope,
                            sessionInfo: CommitSessionInfo,
                            commitChecks: suspend () -> CommitChecksResult) {
    check(isExecuting)
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

  suspend fun runBackgroundBeforeCommitChecks(sessionInfo: CommitSessionInfo,
                                              indicator: ProgressIndicator): CommitProblem? {
    return PartialChangesUtil.underChangeList(project, getBeforeCommitChecksChangelist()) {
      runCommitHandlers(sessionInfo, indicator)
    }
  }

  private suspend fun runCommitHandlers(sessionInfo: CommitSessionInfo,
                                        indicator: ProgressIndicator): CommitProblem? {
    try {
      val handlers = commitHandlers
      val commitChecks = handlers
        .map { it.asCommitCheck(sessionInfo, commitContext) }
        .groupBy { it.getExecutionOrder() }

      runCommitChecks(project, commitChecks[CommitCheck.ExecutionOrder.EARLY], indicator)?.let { return it }

      runMetaHandlers(handlers.filterIsInstance<CheckinMetaHandler>())

      runCommitChecks(project, commitChecks[CommitCheck.ExecutionOrder.MODIFICATION], indicator)?.let { return it }
      FileDocumentManager.getInstance().saveAllDocuments()

      runCommitChecks(project, commitChecks[CommitCheck.ExecutionOrder.LATE], indicator)?.let { return it }

      return null // checks passed
    }
    catch (e: Throwable) {
      // Do not report error on cancellation
      // DO report error if someone threw PCE for no reason, ex: IDEA-234006
      if (e is ProcessCanceledException && indicator.isCanceled) throw e

      LOG.warn(Throwable(e))
      return CommitProblem.createError(e)
    }
  }

  companion object {
    private suspend fun runCommitChecks(project: Project,
                                        commitChecks: List<CommitCheck>?,
                                        indicator: ProgressIndicator): CommitProblem? {
      for (commitCheck in commitChecks.orEmpty()) {
        val problem = runCommitCheck(project, commitCheck, indicator)
        if (problem != null) return problem
      }
      return null
    }
  }
}
