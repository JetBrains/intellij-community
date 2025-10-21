// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.ForegroundEvaluationStep
import com.intellij.cce.evaluation.HeadlessEvaluationAbortHandler
import com.intellij.cce.util.CommandLineProgress
import com.intellij.cce.util.Progress
import com.intellij.cce.util.TeamcityProgress
import com.intellij.cce.util.isUnderTeamCity
import com.intellij.cce.workspace.EvaluationWorkspace
import kotlin.coroutines.cancellation.CancellationException

interface BackgroundEvaluationStep : EvaluationStep {
  suspend fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace
}

suspend fun EvaluationStep.run(workspace: EvaluationWorkspace): EvaluationWorkspace? {
  return when (this) {
    is ForegroundEvaluationStep -> start(workspace)
    is BackgroundEvaluationStep -> {
      createProgress(name).wrapWithProgress {
        try {
          runInBackground(workspace, it)
        }
        catch (e: CancellationException) {
          evaluationAbortedHandler.onCancel(name)
          throw e
        }
        catch (e: Throwable) {
          evaluationAbortedHandler.onError(e, name)
          null
        }
      }
    }
    else -> throw IllegalStateException("Unexpected type of `$this`")
  }
}

private val evaluationAbortedHandler = HeadlessEvaluationAbortHandler()

private fun createProgress(title: String) = when {
  isUnderTeamCity -> TeamcityProgress(title)
  else -> CommandLineProgress(title)
}
