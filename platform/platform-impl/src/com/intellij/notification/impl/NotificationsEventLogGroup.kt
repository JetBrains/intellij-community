// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByInlineRegexp
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.impl.NotificationCollector.NotificationPlace
import com.intellij.notification.impl.NotificationCollector.NotificationSeverity
import java.util.stream.Collectors

class NotificationsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    @JvmField
    val GROUP = EventLogGroup("notifications", 65)

    @JvmField
    val DISPLAY_TYPE: EnumEventField<NotificationDisplayType> = Enum("display_type", NotificationDisplayType::class.java)

    @JvmField
    val SEVERITY: EnumEventField<NotificationSeverity> = Enum("severity", NotificationSeverity::class.java)

    @JvmField
    val IS_EXPANDABLE = Boolean("is_expandable")

    @JvmField
    val ID: StringEventField = StringValidatedByInlineRegexp("id", "\\d+.\\d+")

    @JvmField
    val NOTIFICATION_ID = NotificationIdField()

    @JvmField
    val ADDITIONAL = ObjectEventField("additional", NOTIFICATION_ID)

    @JvmField
    val NOTIFICATION_GROUP_ID = StringValidatedByCustomRule("notification_group", "notification_group")

    @JvmField
    val NOTIFICATION_PLACE: EnumEventField<NotificationPlace> = Enum("notification_place", NotificationPlace::class.java)

    @JvmField
    val SHOWN = registerNotificationEvent("shown", DISPLAY_TYPE, SEVERITY, IS_EXPANDABLE)

    @JvmField
    val LOGGED = registerNotificationEvent("logged", SEVERITY)

    @JvmField
    val CLOSED_BY_USER = registerNotificationEvent("closed.by.user")

    @JvmField
    val ACTION_INVOKED = registerNotificationEvent("action.invoked", ActionsEventLogGroup.ACTION_CLASS,
                                                   ActionsEventLogGroup.ACTION_ID, ActionsEventLogGroup.ACTION_PARENT, NOTIFICATION_PLACE)

    @JvmField
    val HYPERLINK_CLICKED = registerNotificationEvent("hyperlink.clicked")

    @JvmField
    val EVENT_LOG_BALLOON_SHOWN = registerNotificationEvent("event.log.balloon.shown")

    @JvmField
    val SETTINGS_CLICKED = registerNotificationEvent("settings.clicked")

    @JvmField
    val BALLOON_EXPANDED = registerNotificationEvent("balloon.expanded")

    @JvmField
    val BALLOON_COLLAPSED = registerNotificationEvent("balloon.collapsed")

    fun registerNotificationEvent(eventId: String, vararg extraFields: EventField<*>): VarargEventId {
      return GROUP.registerVarargEvent(
        eventId,
        ID,
        ADDITIONAL,
        NOTIFICATION_GROUP_ID,
        EventFields.PluginInfo,
        *extraFields
      )
    }
  }

  class NotificationIdField : StringEventField("display_id") {
    override val validationRule: List<String>
      get() {
        val validationRules = NotificationIdsHolder.EP_NAME.extensionList.stream()
          .flatMap { holder: NotificationIdsHolder -> holder.notificationIds.stream() }
          .collect(Collectors.toList())
        validationRules.add(NotificationCollector.UNKNOWN)
        return listOf("{enum:${validationRules.joinToString("|")}}", "{util#notification_display_id}")
      }
  }
}