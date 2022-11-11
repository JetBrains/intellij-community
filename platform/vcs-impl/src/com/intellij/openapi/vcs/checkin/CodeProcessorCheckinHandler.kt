// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

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

  override suspend fun runCheck(): CommitProblem? {
    val sink = coroutineContext.progressSink
    getProgressMessage()?.let {
      sink?.text(it)
    }

    val processor = createCodeProcessor()

    withContext(Dispatchers.Default + noTextSinkContext(sink)) {
      // TODO suspending code processor
      runUnderIndicator {
        processor.processFilesUnderProgress(ProgressManager.getGlobalProgressIndicator())
      }
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    return null
  }
}

internal fun noTextSinkContext(sink: ProgressSink?): CoroutineContext {
  if (sink == null) {
    return EmptyCoroutineContext
  }
  else {
    return NoTextProgressSink(sink).asContextElement()
  }
}

internal class NoTextProgressSink(private val sink: ProgressSink) : ProgressSink {

  override fun update(text: @ProgressText String?, details: @ProgressDetails String?, fraction: Double?) {
    sink.update(text = null, details, fraction)
  }
}
