// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.notification.impl.NotificationCollector.*
import java.util.stream.Collectors

internal object NotificationsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("notifications", 68)

  @JvmField
  val DISPLAY_TYPE: EnumEventField<NotificationDisplayType> = Enum("display_type", NotificationDisplayType::class.java)

  @JvmField
  val SEVERITY: EnumEventField<NotificationSeverity> = Enum("severity", NotificationSeverity::class.java)

  @JvmField
  val IS_EXPANDABLE: BooleanEventField = Boolean("is_expandable")

  @JvmField
  val ID: StringEventField = StringValidatedByInlineRegexp("id", "\\d+.\\d+")

  @JvmField
  val NOTIFICATION_ID: NotificationIdField = NotificationIdField()

  @JvmField
  val ADDITIONAL: ObjectEventField = ObjectEventField("additional", NOTIFICATION_ID)

  @JvmField
  val NOTIFICATION_GROUP_ID: StringEventField = StringValidatedByCustomRule("notification_group", NotificationGroupValidator::class.java)

  @JvmField
  val NOTIFICATION_PLACE: EnumEventField<NotificationPlace> = Enum("notification_place", NotificationPlace::class.java)

  @JvmField
  val SHOWN: VarargEventId = registerNotificationEvent("shown", DISPLAY_TYPE, SEVERITY, IS_EXPANDABLE)

  @JvmField
  val LOGGED: VarargEventId = registerNotificationEvent("logged", SEVERITY)

  @JvmField
  val CLOSED_BY_USER: VarargEventId = registerNotificationEvent("closed.by.user")

  @JvmField
  val ACTION_INVOKED: VarargEventId = registerNotificationEvent("action.invoked", ActionsEventLogGroup.ACTION_CLASS,
                                                                ActionsEventLogGroup.ACTION_ID, ActionsEventLogGroup.ACTION_PARENT,
                                                                NOTIFICATION_PLACE)

  @JvmField
  val HYPERLINK_CLICKED: VarargEventId = registerNotificationEvent("hyperlink.clicked")

  @JvmField
  val EVENT_LOG_BALLOON_SHOWN: VarargEventId = registerNotificationEvent("event.log.balloon.shown")

  @JvmField
  val SETTINGS_CLICKED: VarargEventId = registerNotificationEvent("settings.clicked")

  @JvmField
  val BALLOON_EXPANDED: VarargEventId = registerNotificationEvent("balloon.expanded")

  @JvmField
  val BALLOON_COLLAPSED: VarargEventId = registerNotificationEvent("balloon.collapsed")

  private fun registerNotificationEvent(eventId: String, vararg extraFields: EventField<*>): VarargEventId {
    return GROUP.registerVarargEvent(
      eventId,
      ID,
      ADDITIONAL,
      NOTIFICATION_GROUP_ID,
      EventFields.PluginInfo,
      *extraFields
    )
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
