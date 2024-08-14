// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.withProgressText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class CodeProcessorCheckinHandler(
  val project: Project
) : CheckinHandler(),
    CommitCheck {

  val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  protected open fun getProgressMessage(): @NlsContexts.ProgressText String? = null
  protected abstract fun createCodeProcessor(files: List<VirtualFile>): AbstractLayoutCodeProcessor

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val affectedFiles = commitInfo.committedVirtualFiles

    withContext(Dispatchers.Default) {
      withProgressText(getProgressMessage()) {
        // TODO suspending code processor
        val processor = readAction { createCodeProcessor(affectedFiles) }
        coroutineToIndicator {
          processor.processFilesUnderProgress(ProgressManager.getGlobalProgressIndicator())
        }
      }
    }

    writeIntentReadAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    return null
  }
}
