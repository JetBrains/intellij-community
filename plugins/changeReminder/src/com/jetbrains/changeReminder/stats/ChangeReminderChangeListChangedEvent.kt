// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.predict.PredictionData

internal class ChangeReminderChangeListChangedEvent(
  private val prevModifiedFiles: Collection<FilePath>,
  private val displayedPredictionData: PredictionData,
  private val curModifiedFiles: Collection<FilePath>
) : ChangeReminderUserEvent {
  override val eventType = ChangeReminderEventType.CHANGELIST_CHANGED

  override fun addToLogData(logData: FeatureUsageData) {
    logData.addChangeReminderLogData(ChangeReminderEventDataKey.PREV_MODIFIED_FILES, prevModifiedFiles.anonymizeFilePathCollection())
    logData.addPredictionData(displayedPredictionData)
    logData.addChangeReminderLogData(ChangeReminderEventDataKey.CUR_MODIFIED_FILES, curModifiedFiles.anonymizeFilePathCollection())
  }
}