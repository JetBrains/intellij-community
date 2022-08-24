// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.DumbService.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblemWithDetails
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
    suspend fun runMetaHandlers(project: Project,
                                metaHandlers: List<CheckinMetaHandler>,
                                commitProgressUi: CommitProgressUi,
                                indicator: ProgressIndicator) {
      // reversed to have the same order as when wrapping meta handlers into each other
      for (metaHandler in metaHandlers.reversed()) {
        if (metaHandler is CommitCheck) {
          runCommitCheck(project, metaHandler, commitProgressUi, indicator)
        }
        else {
          suspendCancellableCoroutine<Unit> { continuation ->
            val handlerCall = wrapWithCommitMetaHandler(metaHandler) { continuation.resume(Unit) }
            handlerCall.run()
          }
        }
      }
    }

    suspend fun runCommitChecks(project: Project,
                                commitChecks: List<CommitCheck>,
                                commitProgressUi: CommitProgressUi,
                                indicator: ProgressIndicator): Boolean {
      for (commitCheck in commitChecks) {
        val success = runCommitCheck(project, commitCheck, commitProgressUi, indicator)
        if (!success) return false
      }
      return true
    }

    /**
     * @return true if there are no errors and commit shall proceed
     */
    private suspend fun runCommitCheck(project: Project,
                                       commitCheck: CommitCheck,
                                       commitProgressUi: CommitProgressUi,
                                       indicator: ProgressIndicator): Boolean {
      if (!commitCheck.isEnabled()) return true.also { LOG.debug("Commit check disabled $commitCheck") }
      if (isDumb(project) && !isDumbAware(commitCheck)) return true.also { LOG.debug("Skipped commit check in dumb mode $commitCheck") }

      LOG.debug("Running commit check $commitCheck")
      indicator.checkCanceled()
      indicator.text = ""
      indicator.text2 = ""

      try {
        val problem = commitCheck.runCheck(indicator)
        if (problem != null) {
          if (problem is CommitProblemWithDetails) {
            commitProgressUi.addCommitCheckFailure(problem.text) { problem.showDetails(project) }
          }
          else {
            commitProgressUi.addCommitCheckFailure(problem.text, null)
          }
        }
        return problem == null
      }
      catch (e: Throwable) {
        // Do not report error on cancellation
        // DO report error if someone threw PCE for no reason, ex: IDEA-234006
        if (e is ProcessCanceledException && indicator.isCanceled) throw e
        LOG.warn(Throwable(e))

        val err = e.message
        val message = when {
          err.isNullOrBlank() -> VcsBundle.message("before.checkin.error.unknown")
          else -> VcsBundle.message("before.checkin.error.unknown.details", err)
        }
        commitProgressUi.addCommitCheckFailure(message, null)

        return false
      }
    }
  }
}