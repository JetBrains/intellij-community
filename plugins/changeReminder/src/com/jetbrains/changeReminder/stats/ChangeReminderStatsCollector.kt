// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.EventLogConfiguration.anonymize
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.changeReminder.predict.PredictionData
import java.util.*

internal data class ChangeReminderAnonymousPath(val value: String)

internal enum class ChangeReminderEventType {
  CHANGELIST_CHANGED,
  CHANGES_COMMITTED,
  NODE_EXPANDED;

  override fun toString() = name.toLowerCase(Locale.ENGLISH)
}

internal enum class ChangeReminderEventDataKey {
  COMMITTED_FILES,
  DISPLAYED_PREDICTION,
  CUR_MODIFIED_FILES,
  PREV_MODIFIED_FILES,
  PREDICTION_FOR_FILES,
  EMPTY_REASON;

  override fun toString() = name.toLowerCase(Locale.ENGLISH)
}

internal fun VirtualFile.anonymize(): ChangeReminderAnonymousPath = ChangeReminderAnonymousPath(anonymize(path))

internal fun FilePath.anonymize(): ChangeReminderAnonymousPath = ChangeReminderAnonymousPath(anonymize(path))

internal fun Collection<VirtualFile>.anonymizeVirtualFileCollection(): Collection<ChangeReminderAnonymousPath> = this.map { it.anonymize() }

internal fun Collection<FilePath>.anonymizeFilePathCollection(): Collection<ChangeReminderAnonymousPath> = this.map { it.anonymize() }

internal fun FeatureUsageData.addPredictionData(predictionData: PredictionData) {
  when (predictionData) {
    is PredictionData.Prediction -> {
      addChangeReminderLogData(
        ChangeReminderEventDataKey.DISPLAYED_PREDICTION,
        predictionData.predictionToDisplay.anonymizeVirtualFileCollection()
      )
      addChangeReminderLogData(
        ChangeReminderEventDataKey.PREDICTION_FOR_FILES,
        predictionData.requestedFiles.anonymizeFilePathCollection()
      )
    }
    is PredictionData.EmptyPrediction -> {
      addData(ChangeReminderEventDataKey.EMPTY_REASON.toString(), predictionData.reason.toString())
    }
  }
}

internal fun FeatureUsageData.addChangeReminderLogData(key: ChangeReminderEventDataKey, value: Collection<ChangeReminderAnonymousPath>) {
  addData(key.toString(), value.map { it.value })
}

internal fun logEvent(project: Project, event: ChangeReminderUserEvent) {
  val logData = FeatureUsageData()
  event.addToLogData(logData)

  FUCounterUsageLogger.getInstance().logEvent(project, "vcs.change.reminder", event.eventType.toString(), logData)
}