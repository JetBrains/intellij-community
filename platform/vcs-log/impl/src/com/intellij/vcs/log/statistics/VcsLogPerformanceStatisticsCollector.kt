// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogGraphData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.GRAPH_BUILDING_TIME_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.LOG_DATA_LOAD_TIME_FIELD
import com.intellij.vcs.log.statistics.VcsLogPerformanceStatisticsCollector.LOG_JOIN_TIME_FIELD
import com.intellij.vcs.log.util.GraphOptionsUtil.optionKindNames
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.FilterKind
import kotlin.time.Duration

internal object VcsLogPerformanceStatisticsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("vcs.log.performance", 7)

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

  val LOG_DATA_LOAD_TIME_FIELD = EventFields.Long("log_data_load_time_ms", "Time spent loading recent commit data from VCS")
  val LOG_JOIN_TIME_FIELD = EventFields.Long("log_join_time_ms", "Time spent joining new commits with existing log")
  val GRAPH_BUILDING_TIME_FIELD = EventFields.Long("graph_building_time_ms", "Time spent building the permanent graph from commits")
  val UPDATE_DATA_PACK = GROUP.registerIdeActivity("update.data.pack",
                                                   startEventAdditionalFields = arrayOf(VcsLogRepoSizeCollector.VCS_FIELD),
                                                   finishEventAdditionalFields = arrayOf(LOG_DATA_LOAD_TIME_FIELD,
                                                                                         LOG_JOIN_TIME_FIELD,
                                                                                         GRAPH_BUILDING_TIME_FIELD,
                                                                                         VcsLogRepoSizeCollector.COMMIT_COUNT,
                                                                                         VcsLogRepoSizeCollector.BRANCHES_COUNT,
                                                                                         VcsLogRepoSizeCollector.TAGS_COUNT))

  fun startUpdateDataPackActivity(project: Project, vcs: VcsKey): StructuredIdeActivity = UPDATE_DATA_PACK.started(project) {
    listOf(VcsLogRepoSizeCollector.VCS_FIELD.with(VcsLogRepoSizeCollector.getVcsKeySafe(vcs)))
  }

  override fun getGroup() = GROUP
}

internal fun StructuredIdeActivity?.finishUpdateDataPackActivity(
  loadTime: Duration,
  joinTime: Duration,
  buildTime: Duration,
  result: VcsLogGraphData,
) {
  this?.finished {
    listOf(
      LOG_DATA_LOAD_TIME_FIELD.with(loadTime.inWholeMilliseconds),
      LOG_JOIN_TIME_FIELD.with(joinTime.inWholeMilliseconds),
      GRAPH_BUILDING_TIME_FIELD.with(buildTime.inWholeMilliseconds),
      VcsLogRepoSizeCollector.COMMIT_COUNT.with(result.permanentGraph.allCommits.size),
      VcsLogRepoSizeCollector.BRANCHES_COUNT.with(result.refsModel.branches().count()),
      VcsLogRepoSizeCollector.TAGS_COUNT.with(result.refsModel.tags().count()),
    )
  }
}


internal class NullableRoundedLongEventField(override val name: String) : PrimitiveEventField<Long?>() {
  override val validationRule: List<String> get() = listOf("{regexp#integer}")
  override fun addData(fuData: FeatureUsageData, value: Long?) {
    if (value != null) fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}
