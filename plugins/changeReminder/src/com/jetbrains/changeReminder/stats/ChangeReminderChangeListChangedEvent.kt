// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.predict.PredictionData
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.CHANGELIST_CHANGED
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.CUR_MODIFIED_FILES
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.PREV_MODIFIED_FILES

internal class ChangeReminderChangeListChangedEvent(
  private val prevModifiedFiles: Collection<FilePath>,
  private val displayedPredictionData: PredictionData,
  private val curModifiedFiles: Collection<FilePath>
) : ChangeReminderUserEvent {

  override fun logEvent(project: Project) {
    val data = ArrayList<EventPair<*>>()
    data.add(PREV_MODIFIED_FILES.with(prevModifiedFiles.anonymizeFilePathCollection()))
    data.addAll(getPredictionData(displayedPredictionData))
    data.add(CUR_MODIFIED_FILES.with(curModifiedFiles.anonymizeFilePathCollection()))
    CHANGELIST_CHANGED.log(project, data)
  }
}