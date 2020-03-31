// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.predict.PredictionData

internal class ChangeReminderChangesCommittedEvent(
  private val curModifiedFiles: Collection<FilePath>,
  private val committedFiles: Collection<FilePath>,
  private val displayedPredictionData: PredictionData
) : ChangeReminderUserEvent {
  override val eventType = ChangeReminderEventType.CHANGES_COMMITTED

  override fun addToLogData(logData: FeatureUsageData) {
    logData.addChangeReminderLogData(ChangeReminderEventDataKey.CUR_MODIFIED_FILES, curModifiedFiles.anonymizeFilePathCollection())
    logData.addChangeReminderLogData(ChangeReminderEventDataKey.COMMITTED_FILES, committedFiles.anonymizeFilePathCollection())
    logData.addPredictionData(displayedPredictionData)
  }
}