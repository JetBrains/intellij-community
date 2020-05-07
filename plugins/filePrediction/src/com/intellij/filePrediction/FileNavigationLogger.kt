package com.intellij.filePrediction

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project

internal object FileNavigationLogger {
  private const val GROUP_ID = "file.prediction"

  fun logEvent(project: Project,
               event: String,
               sessionId: Int,
               features: FileFeaturesComputationResult,
               filePath: String,
               prevFilePath: String?,
               refsComputation: Long,
               probability: Double? = null) {
    val data = FeatureUsageData().
      addData("session_id", sessionId).
      addAnonymizedPath(filePath).
      addAnonymizedValue("prev_file_path", prevFilePath).
      addData("refs_computation", refsComputation).
      addData("features_computation", features.duration)

    if (probability != null) {
      data.addData("probability", probability)
    }

    for (feature in features.value) {
      feature.value.addToEventData(feature.key, data)
    }
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, event, data)
  }
}