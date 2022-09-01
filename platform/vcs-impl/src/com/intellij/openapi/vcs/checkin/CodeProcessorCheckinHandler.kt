// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Should only be used in Commit Tool Window. Commit Dialog is not supported.
 *
 * @see com.intellij.openapi.vcs.impl.CheckinHandlersManagerImpl.getRegisteredCheckinHandlerFactories
 * @see com.intellij.vcs.commit.NonModalCommitWorkflowHandler.runAllHandlers
 */
abstract class CodeProcessorCheckinHandler(
  val commitPanel: CheckinProjectPanel
) : CheckinHandler(),
    CommitCheck {

  val project: Project get() = commitPanel.project
  val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  protected open fun getProgressMessage(): @NlsContexts.ProgressText String? = null
  protected abstract fun createCodeProcessor(): AbstractLayoutCodeProcessor

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override suspend fun runCheck(indicator: ProgressIndicator): CommitProblem? {
    getProgressMessage()?.let { indicator.text = it }

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
}

internal class NoTextIndicator(indicator: ProgressIndicator) : DelegatingProgressIndicator(indicator) {
  override fun setText(text: String?) = Unit
}