// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

abstract class CodeProcessorCheckinHandler(
  val project: Project
) : CheckinHandler(),
    CommitCheck {

  val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  protected open fun getProgressMessage(): @NlsContexts.ProgressText String? = null
  protected abstract fun createCodeProcessor(files: List<VirtualFile>): AbstractLayoutCodeProcessor

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val sink = coroutineContext.progressSink
    getProgressMessage()?.let {
      sink?.text(it)
    }

    val affectedFiles = commitInfo.committedVirtualFiles

    withContext(Dispatchers.Default + noTextSinkContext(sink)) {
      // TODO suspending code processor
      val processor = readAction { createCodeProcessor(affectedFiles) }
      coroutineToIndicator {
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
