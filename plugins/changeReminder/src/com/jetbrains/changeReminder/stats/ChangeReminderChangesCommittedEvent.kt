// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.jetbrains.changeReminder.predict.PredictionData
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.CHANGES_COMMITTED
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.COMMITTED_FILES
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.CUR_MODIFIED_FILES

internal class ChangeReminderChangesCommittedEvent(
  private val curModifiedFiles: Collection<FilePath>,
  private val committedFiles: Collection<FilePath>,
  private val displayedPredictionData: PredictionData
) : ChangeReminderUserEvent {

  override fun logEvent(project: Project) {
    val data = ArrayList<EventPair<*>>()
    data.add(CUR_MODIFIED_FILES.with(curModifiedFiles.anonymizeFilePathCollection()))
    data.add(COMMITTED_FILES.with(committedFiles.anonymizeFilePathCollection()))
    data.addAll(getPredictionData(displayedPredictionData))
    CHANGES_COMMITTED.log(project, data)
  }
}