// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class CodeProcessorCheckinHandler(
  val commitPanel: CheckinProjectPanel
) : CheckinHandler(),
    CheckinMetaHandler,
    CommitCheck<CommitProblem> {

  val project: Project get() = commitPanel.project
  val settings: VcsConfiguration get() = VcsConfiguration.getInstance(project)

  abstract fun createCodeProcessor(): AbstractLayoutCodeProcessor

  override suspend fun runCheck(): CommitProblem? {
    val processor = createCodeProcessor()

    withContext(Dispatchers.Default) {
      processor.processFilesUnderProgress(EmptyProgressIndicator())
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    return null
  }

  override fun showDetails(problem: CommitProblem) = Unit

  override fun runCheckinHandlers(runnable: Runnable) = Unit
}