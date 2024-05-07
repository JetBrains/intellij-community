// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private val LOG = logger<NonModalCommitWorkflow>()

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  internal fun launchAsyncSession(scope: CoroutineScope,
                                  sessionInfo: CommitSessionInfo,
                                  commitChecks: suspend () -> CommitChecksResult) {
    check(isExecuting) { "Commit session has already finished" }
    scope.launch {
      try {
        fireBeforeCommitChecksStarted(sessionInfo)
        val result = commitChecks()
        fireBeforeCommitChecksEnded(sessionInfo, result)

        if (result.shouldCommit) {
          awaitActionsOnSave()
          performCommit(sessionInfo)
        }
        else {
          endExecution()
        }
      }
      catch (e: CancellationException) {
        LOG.debug("commit process was cancelled", Throwable(e))
        endExecution()
      }
      catch (e: Throwable) {
        LOG.error(e)
        endExecution()
      }
    }
  }

  private suspend fun awaitActionsOnSave() {
    val modalityState = coroutineContext.contextModality()
    val canAwaitChanges = modalityState == ModalityState.nonModal() || modalityState == null

    val actionsOnSaveManager = service<ActionsOnSaveManager>()
    if (canAwaitChanges) {
      if (actionsOnSaveManager.hasPendingActions()) {
        logger<NonModalCommitWorkflow>().info("Awaiting for 'Actions on Save' on commit")
        actionsOnSaveManager.awaitPendingActions()
      }
    }
    else if (actionsOnSaveManager.hasPendingActions()) {
      logger<NonModalCommitWorkflow>().warn("Couldn't wait for 'Actions on Save' on commit, modalityState: $modalityState")
    }
  }
}
