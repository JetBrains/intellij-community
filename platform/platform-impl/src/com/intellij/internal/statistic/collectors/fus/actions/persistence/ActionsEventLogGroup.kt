// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ActionsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    const val ACTION_INVOKED_EVENT_ID = "action.invoked"

    @JvmField
    val GROUP = EventLogGroup("actions", 60)

    @JvmField
    val ACTION_ID = EventFields.StringValidatedByCustomRule("action_id", "action")

    @JvmField
    val ACTION_CLASS = EventFields.StringValidatedByCustomRule("class", "class_name")

    @JvmField
    val ACTION_PARENT = EventFields.StringValidatedByCustomRule("parent", "class_name")

    @JvmField
    val TOGGLE_ACTION = EventFields.Boolean("enable")

    @JvmField
    val CONTEXT_MENU = EventFields.Boolean("context_menu")

    @JvmField
    val DUMB = EventFields.Boolean("dumb")

    @JvmField
    val ADDITIONAL = EventFields.createAdditionalDataField(GROUP.id, ACTION_INVOKED_EVENT_ID)

    @JvmField
    val ACTION_INVOKED = registerActionInvokedEvent(GROUP, ACTION_INVOKED_EVENT_ID, ADDITIONAL, EventFields.Language)

    @JvmStatic
    fun registerActionInvokedEvent(group: EventLogGroup, eventId: String, vararg extraFields: EventField<*>): VarargEventId {
      return group.registerVarargEvent(
        eventId,
        EventFields.PluginInfoFromInstance,
        EventFields.InputEvent,
        EventFields.ActionPlace,
        EventFields.CurrentFile,
        TOGGLE_ACTION,
        CONTEXT_MENU,
        DUMB,
        ACTION_ID,
        ACTION_CLASS,
        ACTION_PARENT,
        *extraFields
      )
    }

    @JvmField
    val CUSTOM_ACTION_INVOKED = GROUP.registerEvent(
      "custom.action.invoked",
      EventFields.StringValidatedByCustomRule("action_id", "action"),
      EventFields.InputEvent)
  }
}