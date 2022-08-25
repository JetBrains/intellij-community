// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.DumbService.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val LOG = logger<NonModalCommitWorkflow>()

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  suspend fun executeBackgroundSession(sessionInfo: CommitSessionInfo, checker: suspend () -> CommitChecksResult) {
    var result: CommitChecksResult = CommitChecksResult.ExecutionError
    try {
      result = checkCommit(sessionInfo, checker)
    }
    finally {
      if (result.shouldCommit) {
        performCommit(sessionInfo)
      }
      else {
        endExecution()
      }
    }
  }

  private suspend fun checkCommit(sessionInfo: CommitSessionInfo, checker: suspend () -> CommitChecksResult): CommitChecksResult {
    var result: CommitChecksResult = CommitChecksResult.ExecutionError

    fireBeforeCommitChecksStarted(sessionInfo)
    try {
      result = checker()
    }
    catch (e: ProcessCanceledException) {
      result = CommitChecksResult.Cancelled
    }
    finally {
      fireBeforeCommitChecksEnded(sessionInfo, result)
    }

    return result
  }

  companion object {
    suspend fun runMetaHandlers(metaHandlers: List<CheckinMetaHandler>) {
      // reversed to have the same order as when wrapping meta handlers into each other
      for (metaHandler in metaHandlers.reversed()) {
        suspendCancellableCoroutine<Unit> { continuation ->
          val handlerCall = wrapWithCommitMetaHandler(metaHandler) { continuation.resume(Unit) }
          handlerCall.run()
        }
      }
    }

    suspend fun runCommitChecks(project: Project,
                                commitChecks: List<CommitCheck>,
                                indicator: ProgressIndicator): CommitProblem? {
      for (commitCheck in commitChecks) {
        val problem = runCommitCheck(project, commitCheck, indicator)
        if (problem != null) return problem
      }
      return null
    }

    private suspend fun runCommitCheck(project: Project,
                                       commitCheck: CommitCheck,
                                       indicator: ProgressIndicator): CommitProblem? {
      if (!commitCheck.isEnabled()) {
        LOG.debug("Commit check disabled $commitCheck")
        return null
      }
      if (isDumb(project) && !isDumbAware(commitCheck)) {
        LOG.debug("Skipped commit check in dumb mode $commitCheck")
        return null
      }

      LOG.debug("Running commit check $commitCheck")
      indicator.checkCanceled()
      indicator.text = ""
      indicator.text2 = ""

      return commitCheck.runCheck(indicator)
    }
  }
}