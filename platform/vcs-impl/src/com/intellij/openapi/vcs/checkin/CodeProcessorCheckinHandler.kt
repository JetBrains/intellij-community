// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.asContextElement
import com.intellij.platform.util.progress.rawProgressReporter
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
    val reporter = coroutineContext.rawProgressReporter
    getProgressMessage()?.let {
      reporter?.text(it)
    }

    val affectedFiles = commitInfo.committedVirtualFiles

    withContext(Dispatchers.Default + noTextSinkContext(reporter)) {
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

internal fun noTextSinkContext(reporter: RawProgressReporter?): CoroutineContext {
  if (reporter == null) {
    return EmptyCoroutineContext
  }
  else {
    return NoTextProgressReporter(reporter).asContextElement()
  }
}

internal class NoTextProgressReporter(private val reporter: RawProgressReporter) : RawProgressReporter {

  override fun details(details: @ProgressDetails String?) {
    reporter.details(details)
  }

  override fun fraction(fraction: Double?) {
    reporter.fraction(fraction)
  }
}
