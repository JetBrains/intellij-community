// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.stats

import com.intellij.openapi.project.Project
import com.jetbrains.changeReminder.predict.PredictionData
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.NODE_EXPANDED

internal class ChangeReminderNodeExpandedEvent(
  private val displayedPredictionResult: PredictionData
) : ChangeReminderUserEvent {

  override fun logEvent(project: Project) {
    NODE_EXPANDED.log(project, getPredictionData(displayedPredictionResult))
  }
}