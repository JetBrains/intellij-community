// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class GradleExecutionPerformanceCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    val EXTERNAL_TASK_ID: EventField<Long> = EventFields.Long("task_id")
    val GRADLE_PLUGIN: StringEventField = EventFields.StringValidatedByCustomRule<TaskPluginValidator>("gradle_plugin")
    val NAME: StringEventField = EventFields.StringValidatedByCustomRule<TaskNameValidator>("name")
    val UP_TO_DATE_COUNT: EventField<Int> = EventFields.Int("up_to_date_count")
    val FROM_CACHE_COUNT: EventField<Int> = EventFields.Int("from_cache_count")
    val EXECUTED: EventField<Int> = EventFields.Int("executed")
    val SUM_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_ms")
    val UP_TO_DATE_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_ms_up_to_date")
    val FROM_CACHE_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_ms_from_cache")
    val FAILED_COUNT: EventField<Int> = EventFields.Int("failed_count")

    val GROUP: EventLogGroup = EventLogGroup("build.gradle.performance", 1)
    val OPERATION_DURATION: EventId2<Long, Long> = registerEvent("operation.duration")
    val BUILD_LOAD: EventId2<Long, Long> = registerEvent("build.loading")
    val SETTINGS_EVALUATION: EventId2<Long, Long> = registerEvent("settings.evaluation")
    val PROJECT_LOADING: EventId2<Long, Long> = registerEvent("project.loading")
    val TASK_GRAPH_CALCULATION: EventId2<Long, Long> = registerEvent("task.graph.calculation")
    val CONTAINER_CALLBACK: EventId2<Long, Long> = registerEvent("container.callback")
    val TASK_EXECUTION_FINISHED: VarargEventId = GROUP.registerVarargEvent(
      "task.execution", EXTERNAL_TASK_ID, SUM_DURATION_MS, NAME, GRADLE_PLUGIN, UP_TO_DATE_COUNT, FROM_CACHE_COUNT, EventFields.Count,
      UP_TO_DATE_DURATION_MS, FROM_CACHE_DURATION_MS, FAILED_COUNT
    )
    val TASK_GRAPH_EXECUTION: VarargEventId = GROUP.registerVarargEvent(
      "task.graph.execution", EXTERNAL_TASK_ID, EventFields.DurationMs, EventFields.Count, UP_TO_DATE_COUNT, FROM_CACHE_COUNT, EXECUTED
    )

    @JvmStatic
    private fun registerEvent(name: String): EventId2<Long, Long> = GROUP.registerEvent(name, EXTERNAL_TASK_ID, EventFields.DurationMs)
  }

  internal class TaskNameValidator : CustomValidationRule() {

    override fun getRuleId(): String = "build_gradle_performance_task_name"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      return ValidationResultType.ACCEPTED
    }
  }

  internal class TaskPluginValidator : CustomValidationRule() {

    override fun getRuleId(): String = "build_gradle_performance_task_plugin"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (data.startsWith("org.jetbrains.") || data.startsWith("org.gradle.")) {
        return ValidationResultType.ACCEPTED
      }
      return ValidationResultType.REJECTED
    }
  }
}
