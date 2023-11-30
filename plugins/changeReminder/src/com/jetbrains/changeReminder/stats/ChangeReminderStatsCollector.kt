// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.changeReminder.predict.PredictionData
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.DISPLAYED_PREDICTION
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.EMPTY_REASON
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.GROUP
import com.jetbrains.changeReminder.stats.ChangeReminderStatsCollector.PREDICTION_FOR_FILES
import java.util.*

internal fun Collection<VirtualFile>.anonymizeVirtualFileCollection(): List<String> = this.map {
  EventLogConfiguration.getInstance().getOrCreate(GROUP.recorder).anonymize(it.path)
}

internal fun Collection<FilePath>.anonymizeFilePathCollection(): List<String> = this.map {
  EventLogConfiguration.getInstance().getOrCreate(GROUP.recorder).anonymize(it.path)
}

internal fun getPredictionData(predictionData: PredictionData): List<EventPair<*>> {
  val data = ArrayList<EventPair<*>>()
  when (predictionData) {
    is PredictionData.Prediction -> {
      data.add(DISPLAYED_PREDICTION.with(predictionData.predictionToDisplay.anonymizeVirtualFileCollection()))
      data.add(PREDICTION_FOR_FILES.with(predictionData.requestedFiles.anonymizeFilePathCollection()))
    }
    is PredictionData.EmptyPrediction -> {
      data.add(EMPTY_REASON.with(predictionData.reason))
    }
  }
  return data
}

object ChangeReminderStatsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  internal val GROUP = EventLogGroup("vcs.change.reminder", 3)
  internal val COMMITTED_FILES = EventFields.StringListValidatedByRegexp("committed_files", "hash")
  internal val DISPLAYED_PREDICTION = EventFields.StringListValidatedByRegexp("displayed_prediction", "hash")
  internal val CUR_MODIFIED_FILES = EventFields.StringListValidatedByRegexp("cur_modified_files", "hash")
  internal val PREV_MODIFIED_FILES = EventFields.StringListValidatedByRegexp("prev_modified_files", "hash")
  internal val PREDICTION_FOR_FILES = EventFields.StringListValidatedByRegexp("prediction_for_files", "hash")
  internal val EMPTY_REASON = EventFields.Enum<PredictionData.EmptyPredictionReason>("empty_reason") {
    it.name.lowercase(Locale.ENGLISH)
  }

  internal val CHANGELIST_CHANGED = GROUP.registerVarargEvent("changelist_changed",
                                                              PREV_MODIFIED_FILES,
                                                              DISPLAYED_PREDICTION,
                                                              PREDICTION_FOR_FILES,
                                                              EMPTY_REASON,
                                                              CUR_MODIFIED_FILES)

  internal val CHANGES_COMMITTED = GROUP.registerVarargEvent("changes_committed",
                                                             CUR_MODIFIED_FILES,
                                                             COMMITTED_FILES,
                                                             DISPLAYED_PREDICTION,
                                                             PREDICTION_FOR_FILES,
                                                             EMPTY_REASON)

  internal val NODE_EXPANDED = GROUP.registerVarargEvent("node_expanded",
                                                         DISPLAYED_PREDICTION,
                                                         PREDICTION_FOR_FILES,
                                                         EMPTY_REASON)
}
