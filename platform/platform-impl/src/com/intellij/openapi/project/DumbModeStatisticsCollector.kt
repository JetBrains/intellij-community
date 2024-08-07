// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object DumbModeStatisticsCollector : CounterUsagesCollector() {
  @JvmStatic
  fun logProcessFinished(activity: StructuredIdeActivity?,
                         finishType: IndexingFinishType) {
    activity?.finished { listOf(EventPair(FINISH_TYPE, finishType)) }
  }

  val GROUP: EventLogGroup = EventLogGroup("dumb.mode", 2)

  @JvmField
  val STAGE_CLASS: ClassEventField = EventFields.Class("stage_class")

  @JvmField
  val FINISH_TYPE: EnumEventField<IndexingFinishType> = EventFields.Enum("finish_type", IndexingFinishType::class.java)

  @JvmField
  val DUMB_MODE_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity("dumb.mode", emptyArray(), arrayOf(FINISH_TYPE))

  @JvmField
  val DUMB_MODE_STAGE_ACTIVITY = GROUP.registerIdeActivity("stage",
                                                           arrayOf(STAGE_CLASS, EventFields.PluginInfo),
                                                           arrayOf(STAGE_CLASS, EventFields.PluginInfo, FINISH_TYPE),
                                                           DUMB_MODE_ACTIVITY)

  override fun getGroup(): EventLogGroup = GROUP

  enum class IndexingFinishType {
    TERMINATED, FINISHED
  }
}