// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IndexingStatisticsCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("indexing", 4)

    @JvmField
    val STAGE_CLASS = EventFields.Class("stage_class")

    @JvmField
    val INDEXING_ACTIVITY = IdeActivityDefinition(GROUP, null, startEventAdditionalFields = arrayOf(EventFields.PluginInfo))

    @JvmField
    val INDEXING_STAGE = INDEXING_ACTIVITY.registerStage("stage", arrayOf(STAGE_CLASS))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}