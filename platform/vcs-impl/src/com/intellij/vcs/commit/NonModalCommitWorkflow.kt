// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.project.DumbService.isDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CommitCheck

private val LOG = logger<NonModalCommitWorkflow>()

abstract class NonModalCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  override fun runBeforeCommitHandler(handler: CheckinHandler, executor: CommitExecutor?): CheckinHandler.ReturnResult {
    if (!handler.acceptExecutor(executor)) return CheckinHandler.ReturnResult.COMMIT
    LOG.debug("CheckinHandler.beforeCheckin: $handler")

    if (handler is CommitCheck) {
      if (!handler.isEnabled())
        return CheckinHandler.ReturnResult.COMMIT.also { LOG.debug("Commit check disabled $handler") }

      if (isDumb(project) && !isDumbAware(handler))
        return CheckinHandler.ReturnResult.COMMIT.also { LOG.debug("Skipped commit check in dumb mode $handler") }
    }

    return handler.beforeCheckin(executor, commitContext.additionalDataConsumer)
  }
}