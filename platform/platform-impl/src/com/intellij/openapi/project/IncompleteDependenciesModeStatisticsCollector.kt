// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IncompleteDependenciesModeStatisticsCollector : CounterUsagesCollector() {
  fun incompleteModeStarted(project: Project?): StructuredIdeActivity {
    return INCOMPLETE_DEPENDENCIES_MODE_ACTIVITY.started(project)
  }

  fun incompleteModeFinished(activity: StructuredIdeActivity?) {
    activity?.finished()
  }

  fun incompleteModeSubtaskStarted(project: Project?,
                                   incompleteModeActivity: StructuredIdeActivity,
                                   requestor: Class<*>,
                                   stateBefore: DependenciesState,
                                   stateAfter: DependenciesState): StructuredIdeActivity {
    return INCOMPLETE_DEPENDENCIES_MODE_SUBTASK_ACTIVITY.startedWithParent(project, incompleteModeActivity) {
      listOf(EventPair(REQUESTOR, requestor), EventPair(STATE_BEFORE, stateBefore), EventPair(STATE_AFTER, stateAfter))
    }
  }

  fun incompleteModeSubtaskFinished(activity: StructuredIdeActivity?, requestor: Class<*>, stateBefore: DependenciesState, stateAfter: DependenciesState) {
    activity?.finished { listOf(EventPair(REQUESTOR, requestor), EventPair(STATE_BEFORE, stateBefore), EventPair(STATE_AFTER, stateAfter)) }
  }

  val GROUP: EventLogGroup = EventLogGroup("incomplete.dependencies.mode", 3)

  @JvmField
  val STATE_BEFORE: EnumEventField<DependenciesState> = EventFields.Enum("state_before", DependenciesState::class.java)
  @JvmField
  val STATE_AFTER: EnumEventField<DependenciesState> = EventFields.Enum("state_after", DependenciesState::class.java)
  @JvmField
  val REQUESTOR: ClassEventField = EventFields.Class("requestor")

  @JvmField
  val INCOMPLETE_DEPENDENCIES_MODE_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity("incomplete_dependencies_mode")
  @JvmField
  val INCOMPLETE_DEPENDENCIES_MODE_SUBTASK_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity("incomplete_dependencies_mode_subtask",
                                                                                                       arrayOf(REQUESTOR, STATE_BEFORE, STATE_AFTER),
                                                                                                       arrayOf(REQUESTOR, STATE_BEFORE, STATE_AFTER),
                                                                                                       parentActivity = INCOMPLETE_DEPENDENCIES_MODE_ACTIVITY,
                                                                                                       subStepWithStepId = true)

  override fun getGroup(): EventLogGroup = GROUP
}