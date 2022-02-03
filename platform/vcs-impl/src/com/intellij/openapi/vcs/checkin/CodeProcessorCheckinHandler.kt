// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [CheckinMetaHandler] is only implemented for correct execution order of [CheckinHandler]-s.
 * To allow running handlers provided by [CheckinHandlerFactory] before the ones provided by [VcsCheckinHandlerFactory].
 *
 * Should only be used in Commit Tool Window. Commit Dialog is not supported.
 *
 * @see com.intellij.openapi.vcs.impl.CheckinHandlersManagerImpl.getRegisteredCheckinHandlerFactories
 * @see com.intellij.vcs.commit.NonModalCommitWorkflowHandler.runAllHandlers
 */
abstract class CodeProcessorCheckinHandler(
  val commitPanel: CheckinProjectPanel
) : CheckinHandler(),
    CheckinMetaHandler,
    CommitCheck<CommitProblem> {

  val project: Project get() = commitPanel.project
  val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  abstract fun createCodeProcessor(): AbstractLayoutCodeProcessor

  override suspend fun runCheck(indicator: ProgressIndicator): CommitProblem? {
    val processor = createCodeProcessor()

    withContext(Dispatchers.Default) {
      val noTextIndicator = NoTextIndicator(indicator)

      ProgressManager.getInstance().executeProcessUnderProgress(
        { processor.processFilesUnderProgress(noTextIndicator) },
        noTextIndicator
      )
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    return null
  }

  /**
   * Does nothing as no problem is reported in [runCheck].
   */
  override fun showDetails(problem: CommitProblem) = Unit

  /**
   * Won't be called for Commit Tool Window as [CommitCheck] is implemented.
   *
   * @see com.intellij.vcs.commit.NonModalCommitWorkflow.runMetaHandler
   */
  override fun runCheckinHandlers(runnable: Runnable) =
    throw UnsupportedOperationException("Commit Dialog is not supported")
}

internal class NoTextIndicator(indicator: ProgressIndicator) : DelegatingProgressIndicator(indicator) {
  override fun setText(text: String?) = Unit
}