// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.PseudoMap
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult.COMMIT
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer

private val LOG = logger<AbstractCommitWorkflow>()

abstract class AbstractCommitWorkflow(val project: Project) {
  val commitContext: CommitContext = CommitContext()

  // TODO Unify with CommitContext
  private val additionalData = PseudoMap<Any, Any>()
  val additionalDataConsumer: PairConsumer<Any, Any> get() = additionalData
  val additionalDataHolder: NullableFunction<Any, Any> get() = additionalData

  fun runBeforeCheckinHandlers(executor: CommitExecutor?, handlers: List<CheckinHandler>): CheckinHandler.ReturnResult {
    handlers.asSequence().filter { it.acceptExecutor(executor) }.forEach { handler ->
      LOG.debug("CheckinHandler.beforeCheckin: $handler")

      val result = handler.beforeCheckin(executor, additionalDataConsumer)
      if (result != COMMIT) return result
    }

    return COMMIT
  }

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