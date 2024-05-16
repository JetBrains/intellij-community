// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IncompleteDependenciesModeStatisticsCollector : CounterUsagesCollector() {
  fun started(project: Project?, stateBefore: DependenciesState, stateAfter: DependenciesState): StructuredIdeActivity {
    return INCOMPLETE_DEPENDENCIES_MODE_ACTIVITY.started(project) {
        listOf(EventPair(STATE_BEFORE, stateBefore), EventPair(STATE_AFTER, stateAfter))
      }
  }

  @JvmStatic
  fun finished(activity: StructuredIdeActivity?, stateBefore: DependenciesState, stateAfter: DependenciesState) {
    activity?.finished { listOf(EventPair(STATE_BEFORE, stateBefore), EventPair(STATE_AFTER, stateAfter)) }
  }

  val GROUP: EventLogGroup = EventLogGroup("incomplete.dependencies.mode", 1)

  @JvmField
  val STATE_BEFORE: EnumEventField<DependenciesState> = EventFields.Enum("state_before", DependenciesState::class.java)
  @JvmField
  val STATE_AFTER: EnumEventField<DependenciesState> = EventFields.Enum("state_after", DependenciesState::class.java)
  @JvmField
  val INCOMPLETE_DEPENDENCIES_MODE_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity("incomplete_dependencies_mode", arrayOf(STATE_BEFORE, STATE_AFTER), arrayOf(STATE_BEFORE, STATE_AFTER))

  override fun getGroup(): EventLogGroup = GROUP
}