// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import kotlin.math.round

internal object FileNavigationLogger {
  private const val GROUP_ID = "file.prediction"

  fun logEvent(project: Project,
               sessionId: Int,
               features: Map<String, FilePredictionFeature>,
               filePath: String,
               prevFilePath: String?,
               source: String,
               opened: Boolean,
               totalDuration: Long,
               featuresComputation: Long,
               refsComputation: Long,
               predictionDuration: Long? = null,
               probability: Double? = null) {
    val data = FeatureUsageData()
      .addData("session_id", sessionId)
      .addAnonymizedPath(filePath)
      .addAnonymizedValue("prev_file_path", prevFilePath)
      .addData("source", source)
      .addData("opened", opened)
      .addData("total_ms", totalDuration)
      .addData("refs_ms", refsComputation)
      .addData("features_ms", featuresComputation)

    if (predictionDuration != null) {
      data.addData("predict_ms", predictionDuration)
    }

    if (probability != null) {
      data.addData("probability", roundProbability(probability))
    }

    for (feature in features) {
      feature.value.addToEventData(feature.key, data)
    }
    FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, "candidate.calculated", data)
  }

  private fun roundProbability(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }
}