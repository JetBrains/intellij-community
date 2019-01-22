// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
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

  private val vcsConfiguration = VcsConfiguration.getInstance(project)

  fun validateCommitMessage(commitMessage: String): Boolean {
    if (vcsConfiguration.FORCE_NON_EMPTY_COMMENT && commitMessage.isEmpty()) {
      val requestForCheckin = Messages.showYesNoDialog(VcsBundle.message("confirmation.text.check.in.with.empty.comment"),
                                                       VcsBundle.message("confirmation.title.check.in.with.empty.comment"),
                                                       Messages.getWarningIcon())
      return requestForCheckin == Messages.YES
    }
    return true
  }

  fun performBeforeCommitChecks(executor: CommitExecutor?,
                                handlers: List<CheckinHandler>,
                                changeList: LocalChangeList): CheckinHandler.ReturnResult {
    val compoundResultRef = Ref.create<CheckinHandler.ReturnResult>()
    val proceedRunnable = Runnable {
      FileDocumentManager.getInstance().saveAllDocuments()
      compoundResultRef.set(runBeforeCheckinHandlers(executor, handlers))
    }

    val runnable = wrapIntoCheckinMetaHandlers(proceedRunnable, handlers)
    doRunBeforeCommitChecks(changeList, runnable)
    return compoundResultRef.get() ?: CheckinHandler.ReturnResult.CANCEL
  }

  abstract fun doRunBeforeCommitChecks(changeList: LocalChangeList, checks: Runnable)

  private fun runBeforeCheckinHandlers(executor: CommitExecutor?, handlers: List<CheckinHandler>): CheckinHandler.ReturnResult {
    handlers.asSequence().filter { it.acceptExecutor(executor) }.forEach { handler ->
      LOG.debug("CheckinHandler.beforeCheckin: $handler")

      val result = handler.beforeCheckin(executor, additionalDataConsumer)
      if (result != COMMIT) return result
    }

    return COMMIT
  }

  private fun wrapIntoCheckinMetaHandlers(runnable: Runnable, handlers: List<CheckinHandler>): Runnable {
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