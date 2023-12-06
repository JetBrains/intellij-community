// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.data.SingleTaskController
import com.jetbrains.changeReminder.ChangeReminderBundle

internal abstract class PredictionController(private val project: Project,
                                             name: String,
                                             parent: Disposable,
                                             handler: (PredictionData) -> Unit
) : SingleTaskController<PredictionRequest, PredictionData>(name, parent, { handler(it) }) {
  var inProgress = false
    private set(value) {
      field = value
      inProgressChanged(value)
    }

  override fun cancelRunningTasks(requests: List<PredictionRequest>) = true

  override fun startNewBackgroundTask(): SingleTask {
    val task: Task.Backgroundable = object : Task.Backgroundable(
      project,
      ChangeReminderBundle.message("prediction.controller.task.background.title")
    ) {
      override fun run(indicator: ProgressIndicator) {
        inProgress = true
        var predictionData: PredictionData? = null
        val request = popRequest() ?: return
        try {
          predictionData = request.calculate()
        }
        catch (e: ProcessCanceledException) {
          predictionData = PredictionData.EmptyPrediction(PredictionData.EmptyPredictionReason.CALCULATION_CANCELED)
          throw e
        }
        catch (_: Exception) {
          predictionData = PredictionData.EmptyPrediction(PredictionData.EmptyPredictionReason.EXCEPTION_THROWN)
        }
        finally {
          complete(predictionData ?: PredictionData.EmptyPrediction(PredictionData.EmptyPredictionReason.UNEXPECTED_REASON))
        }
      }
    }
    val indicator = EmptyProgressIndicator()
    val future = (ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressAsynchronously(task, indicator, null)
    return SingleTaskImpl(future, indicator)
  }

  abstract fun inProgressChanged(value: Boolean)

  private fun complete(result: PredictionData) {
    inProgress = false
    taskCompleted(result)
  }
}