// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler

private val LOG = logger<AbstractCommitWorkflow>()

abstract class AbstractCommitWorkflow(val project: Project) {
  val commitContext: CommitContext = CommitContext()

  fun wrapIntoCheckinMetaHandlers(runnable: Runnable, handlers: List<CheckinHandler>): Runnable {
    var result = runnable
    handlers.filterIsInstance<CheckinMetaHandler>().forEach {
      val previousResult = result
      result = Runnable {
        LOG.debug("CheckinMetaHandler.runCheckinHandlers: $it")
        it.runCheckinHandlers(previousResult)
      }
    }
    return result
  }
}