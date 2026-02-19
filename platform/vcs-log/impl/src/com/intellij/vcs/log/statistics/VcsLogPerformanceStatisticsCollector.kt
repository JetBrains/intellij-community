// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.util.GraphOptionsUtil.optionKindNames
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.FilterKind

internal object VcsLogPerformanceStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("vcs.log.performance", 6)

  val FILE_HISTORY_COMPUTING = GROUP.registerEvent("file.history.computed",
                                                   VcsLogRepoSizeCollector.VCS_FIELD,
                                                   EventFields.Boolean("with_index"),
                                                   EventFields.DurationMs)
  val FILE_HISTORY_COLLECTING_RENAMES = GROUP.registerEvent("file.history.collected.renames",
                                                            VcsLogRepoSizeCollector.VCS_FIELD,
                                                            EventFields.DurationMs)

  val VCS_LIST_FIELD = object : StringListEventField("vcs_list") {
    override val description: String? = null
    override val validationRule: List<String> get() = VcsLogRepoSizeCollector.getVcsValidationRule()
  }
  val FILTERS_FIELD = EventFields.StringList("filters", VcsLogFilterCollection.STANDARD_KEYS.map { it.name })
  val SORT_TYPE_FIELD = EventFields.String("sort_type", PermanentGraph.SortType.entries.map { it.presentation })
  val GRAPH_OPTIONS_TYPE_FIELD = EventFields.String("graph_options_type", optionKindNames)
  val FILTERED_COMMIT_COUNT_FIELD = object : StringEventField("filtered_commit_count") {
    override val validationRule: List<String> get() = listOf("{regexp#integer}", "{enum:${CommitCountStage.ALL}}")
  }
  val REPOSITORY_COMMIT_COUNT_FIELD = NullableRoundedLongEventField("repository_commit_count")
  val FILTER_KIND_FIELD = EventFields.String("filter_kind", FilterKind.entries.map { it.name })
  val VCS_LOG_FILTER = GROUP.registerVarargEvent("vcs.log.filtered", VCS_LIST_FIELD, FILTERS_FIELD, GRAPH_OPTIONS_TYPE_FIELD,
                                                 SORT_TYPE_FIELD, REPOSITORY_COMMIT_COUNT_FIELD, FILTERED_COMMIT_COUNT_FIELD, FILTER_KIND_FIELD,
                                                 EventFields.DurationMs)

  override fun getGroup() = GROUP
}

internal class NullableRoundedLongEventField(override val name: String) : PrimitiveEventField<Long?>() {
  override val validationRule: List<String> get() = listOf("{regexp#integer}")
  override fun addData(fuData: FeatureUsageData, value: Long?) {
    if (value != null) fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}
