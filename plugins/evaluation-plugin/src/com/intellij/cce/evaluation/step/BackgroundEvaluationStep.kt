// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.HeadlessEvaluationAbortHandler
import com.intellij.cce.util.CommandLineProgress
import com.intellij.cce.util.Progress
import com.intellij.cce.util.TeamcityProgress
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.FutureResult

abstract class BackgroundEvaluationStep(protected val project: Project) : EvaluationStep {
  protected companion object {
    val LOG = Logger.getInstance(BackgroundEvaluationStep::class.java)
  }

  abstract fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    val result = FutureResult<EvaluationWorkspace?>()
    val task = object : Task.Backgroundable(project, name, true) {
      override fun run(indicator: ProgressIndicator) {
        createProgress(title).wrapWithProgress {
          result.set(runInBackground(workspace, it))
        }
      }

      override fun onCancel() {
        evaluationAbortedHandler.onCancel(this.title)
        result.set(null)
      }

      override fun onThrowable(error: Throwable) {
        evaluationAbortedHandler.onError(error, this.title)
        result.setException(error)
      }
    }
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    return result.get()
  }

  private val evaluationAbortedHandler = HeadlessEvaluationAbortHandler()

  private fun createProgress(title: String) = when {
    System.getenv("TEAMCITY_VERSION") != null -> TeamcityProgress(title)
    else -> CommandLineProgress(title)
  }
}
