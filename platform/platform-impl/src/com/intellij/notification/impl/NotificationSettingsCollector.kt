// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.notification.NotificationGroupManager

internal class NotificationSettingsCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("notification.settings", 2)
  private val SHOULD_LOG = EventFields.Boolean("should_log")
  private val PLAY_SOUND = EventFields.Boolean("play_sound")
  private val READ_ALOUD = EventFields.Boolean("read_aloud")
  private val CHANGED = GROUP.registerVarargEvent(
    "changed",
    NotificationsEventLogGroup.NOTIFICATION_GROUP_ID,
    NotificationsEventLogGroup.DISPLAY_TYPE,
    SHOULD_LOG,
    PLAY_SOUND,
    READ_ALOUD
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()
    for (group in NotificationGroupManager.getInstance().registeredNotificationGroups) {
      val changedSettings = NotificationsConfigurationImpl.getSettings(group.displayId)
      val defaultSettings = NotificationSettings(group.displayId, group.displayType, group.isLogByDefault, false)
      if (changedSettings != defaultSettings) {
        result.add(CHANGED.metric(
          NotificationsEventLogGroup.NOTIFICATION_GROUP_ID with group.displayId,
          NotificationsEventLogGroup.DISPLAY_TYPE with changedSettings.displayType,
          SHOULD_LOG with changedSettings.isShouldLog,
          PLAY_SOUND with changedSettings.isPlaySound,
          READ_ALOUD with changedSettings.isShouldReadAloud
        ))
      }
    }
    return result
  }
}
