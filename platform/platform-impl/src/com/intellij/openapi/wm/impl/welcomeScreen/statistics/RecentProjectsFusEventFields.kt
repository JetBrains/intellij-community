// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.statistics

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.openapi.actionSystem.DataKey

internal class RecentProjectsFusEventFields : FeatureUsageCollectorExtension {
  override fun getGroupId(): String = ActionsEventLogGroup.GROUP.id

  override fun getEventId(): String = ActionsEventLogGroup.ACTION_FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> = listOf(INDEX)

  companion object {
    val ROW_KEY: DataKey<Int> = DataKey.create("RecentProjects.row")

    val INDEX: PrimitiveEventField<Int> = EventFields.LimitedInt("recent_project_index", 0..50)
  }
}
