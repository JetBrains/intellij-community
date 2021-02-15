// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.DumbService.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val LOG = logger<NonModalCommitWorkflow>()

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  override fun runBeforeCommitHandler(handler: CheckinHandler, executor: CommitExecutor?): CheckinHandler.ReturnResult {
    if (!handler.acceptExecutor(executor)) return CheckinHandler.ReturnResult.COMMIT
    LOG.debug("CheckinHandler.beforeCheckin: $handler")

    if (handler is CommitCheck<*>) {
      if (!handler.isEnabled())
        return CheckinHandler.ReturnResult.COMMIT.also { LOG.debug("Commit check disabled $handler") }

      if (isDumb(project) && !isDumbAware(handler))
        return CheckinHandler.ReturnResult.COMMIT.also { LOG.debug("Skipped commit check in dumb mode $handler") }
    }

    return handler.beforeCheckin(executor, commitContext.additionalDataConsumer)
  }

  suspend fun executeDefault(checker: suspend () -> CheckinHandler.ReturnResult) {
    var result = CheckinHandler.ReturnResult.CANCEL
    try {
      result = checkCommit(checker)
      processExecuteDefaultChecksResult(result)
    }
    finally {
      if (result != CheckinHandler.ReturnResult.COMMIT) endExecution()
    }
  }

  private suspend fun checkCommit(checker: suspend () -> CheckinHandler.ReturnResult): CheckinHandler.ReturnResult {
    var result = CheckinHandler.ReturnResult.CANCEL

    fireBeforeCommitChecksStarted()
    try {
      result = checker()
    }
    finally {
      fireBeforeCommitChecksEnded(true, result)
    }

    return result
  }

  suspend fun runMetaHandlers() =
    commitHandlers
      .filterIsInstance<CheckinMetaHandler>()
      .reversed() // to have the same order as when wrapping meta handlers into each other
      .forEach { runMetaHandler(it) }

  private suspend fun runMetaHandler(metaHandler: CheckinMetaHandler) {
    if (metaHandler is CommitCheck<*>) {
      runCommitCheck(metaHandler)
    }
    else {
      suspendCancellableCoroutine<Unit> { continuation ->
        val handlerCall = wrapWithCommitMetaHandler(metaHandler) { continuation.resume(Unit) }
        handlerCall.run()
      }
    }
  }

  fun runHandlers(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    val handlers = commitHandlers.filterNot { it is CommitCheck<*> }
    return runBeforeCommitHandlersChecks(executor, handlers)
  }

  suspend fun <P : CommitProblem> runCommitCheck(commitCheck: CommitCheck<P>): P? {
    if (!commitCheck.isEnabled()) return null.also { LOG.debug("Commit check disabled $commitCheck") }
    if (isDumb(project) && !isDumbAware(commitCheck)) return null.also { LOG.debug("Skipped commit check in dumb mode $commitCheck") }

    LOG.debug("Running commit check $commitCheck")
    return commitCheck.runCheck()
  }
}