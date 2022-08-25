// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.DumbService.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.impl.PartialChangesUtil
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
      val (modificationHandlers, plainHandlers) = handlers.partition { it is CheckinModificationHandler }
      val modificationCommitChecks = modificationHandlers.map { it.asCommitCheck(sessionInfo, commitContext) }
      val commitChecks = plainHandlers.map { it.asCommitCheck(sessionInfo, commitContext) }

      val metaHandlers = handlers.filterIsInstance<CheckinMetaHandler>()
      runMetaHandlers(metaHandlers)

      runCommitChecks(project, modificationCommitChecks, indicator)?.let { return it }
      FileDocumentManager.getInstance().saveAllDocuments()

      runCommitChecks(project, commitChecks, indicator)?.let { return it }

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

private fun CheckinHandler.asCommitCheck(sessionInfo: CommitSessionInfo, commitContext: CommitContext): CommitCheck {
  if (this is CommitCheck) return this
  return ProxyCommitCheck(this, sessionInfo, commitContext)
}

private class ProxyCommitCheck(private val checkinHandler: CheckinHandler,
                               private val sessionInfo: CommitSessionInfo,
                               private val commitContext: CommitContext) : CommitCheck {
  override fun isDumbAware(): Boolean {
    return isDumbAware(checkinHandler)
  }

  override fun isEnabled(): Boolean = checkinHandler.acceptExecutor(sessionInfo.executor)

  override suspend fun runCheck(indicator: ProgressIndicator): CommitProblem? {
    val result = checkinHandler.beforeCheckin(sessionInfo.executor, commitContext.additionalDataConsumer)
    if (result == null || result == CheckinHandler.ReturnResult.COMMIT) return null
    return UnknownCommitProblem()
  }

  override fun toString(): String {
    return "ProxyCommitCheck: $checkinHandler"
  }
}

internal class UnknownCommitProblem : CommitProblem {
  override val text: String get() = VcsBundle.message("before.checkin.error.unknown")
}
