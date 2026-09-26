// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleTaskClassifier

@ApiStatus.Internal
object GradleTaskExecutionCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  val EXTERNAL_TASK_ID: EventField<Long> = EventFields.Long("task_id")
  val GRADLE_PLUGIN: StringEventField = EventFields.StringValidatedByCustomRule<TaskPluginValidator>("gradle_plugin")
  val NAME: StringEventField = EventFields.StringValidatedByCustomRule<TaskNameValidator>("name")
  val UP_TO_DATE_COUNT: EventField<Int> = EventFields.Int("up_to_date_count")
  val FROM_CACHE_COUNT: EventField<Int> = EventFields.Int("from_cache_count")
  val SUM_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_ms")
  val UP_TO_DATE_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_up_to_date_ms")
  val FROM_CACHE_DURATION_MS: EventField<Long> = EventFields.Long("sum_duration_from_cache_ms")
  val FAILED_COUNT: EventField<Int> = EventFields.Int("failed_count")

  val GROUP: EventLogGroup = EventLogGroup("build.gradle.performance", 2)
  val TASK_EXECUTED: VarargEventId = GROUP.registerVarargEvent(
    "task.executed", EXTERNAL_TASK_ID, SUM_DURATION_MS, NAME, GRADLE_PLUGIN, UP_TO_DATE_COUNT, FROM_CACHE_COUNT, EventFields.Count,
    UP_TO_DATE_DURATION_MS, FROM_CACHE_DURATION_MS, FAILED_COUNT
  )

  internal class TaskNameValidator : CustomValidationRule() {

    override fun getRuleId(): String = "build_gradle_performance_task_name"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (GradleTaskClassifier.isClassified(data)) {
        return ValidationResultType.ACCEPTED
      }
      return ValidationResultType.THIRD_PARTY
    }
  }

  internal class TaskPluginValidator : CustomValidationRule() {

    override fun getRuleId(): String = "build_gradle_performance_task_plugin"

    override fun doValidate(data: String, context: EventContext): ValidationResultType {
      if (data.startsWith("org.jetbrains.") || data.startsWith("org.gradle.")) {
        return ValidationResultType.ACCEPTED
      }
      return ValidationResultType.THIRD_PARTY
    }
  }
}
