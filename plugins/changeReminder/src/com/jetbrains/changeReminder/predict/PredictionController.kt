// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.Consumer
import com.intellij.vcs.log.data.SingleTaskController
import com.jetbrains.changeReminder.measureSupplierTimeMillis
import com.jetbrains.changeReminder.stats.ChangeReminderData
import com.jetbrains.changeReminder.stats.ChangeReminderEvent
import com.jetbrains.changeReminder.stats.logEvent

internal abstract class PredictionController(private val project: Project,
                                             name: String,
                                             parent: Disposable,
                                             handler: (Collection<FilePath>) -> Unit
) : SingleTaskController<PredictionRequest, Collection<FilePath>>(project, name, Consumer { handler(it) }, parent) {
  var inProgress = false
    private set(value) {
      field = value
      inProgressChanged(value)
    }

  override fun cancelRunningTasks(requests: Array<out PredictionRequest>) = true

  override fun startNewBackgroundTask(): SingleTask {
    val task: Task.Backgroundable = object : Task.Backgroundable(project, "ChangeReminder Prediction Calculating") {
      override fun run(indicator: ProgressIndicator) {
        inProgress = true
        try {
          val request = popRequest() ?: let {
            complete(emptyList())
            return
          }
          val (time, prediction) = measureSupplierTimeMillis { request.calculate() }
          logEvent(project, ChangeReminderEvent.PREDICTION_CALCULATED, ChangeReminderData.EXECUTION_TIME, time)
          complete(prediction)
        }
        catch (e: ProcessCanceledException) {
          complete(emptyList())
          throw e
        }
      }
    }
    val indicator = EmptyProgressIndicator()
    val future = (ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressAsynchronously(task, indicator, null)
    return SingleTaskImpl(future, indicator)
  }

  abstract fun inProgressChanged(value: Boolean)

  private fun complete(result: Collection<FilePath>) {
    inProgress = false
    taskCompleted(result)
  }
}