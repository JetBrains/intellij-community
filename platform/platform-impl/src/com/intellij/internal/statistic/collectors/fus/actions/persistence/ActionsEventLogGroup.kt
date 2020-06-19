// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger.configVersion
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ActionsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    const val ACTION_INVOKED_EVENT_ID = "action.invoked"
    @JvmField val GROUP = EventLogGroup("actions", configVersion)
    @JvmField val ACTION_ID = EventFields.String("action_id").withCustomRule("action")
    @JvmField val ACTION_CLASS = EventFields.String("class").withCustomRule("class")
    @JvmField val ACTION_PARENT = EventFields.String("parent").withCustomRule("class")
    @JvmField val CONTEXT_MENU = EventFields.Boolean("context_menu")
    @JvmField val DUMB = EventFields.Boolean("dumb")

    @JvmField val ADDITIONAL = EventFields.createAdditionalDataField(GROUP.id, ACTION_INVOKED_EVENT_ID)
    @JvmField val ACTION_INVOKED = registerActionInvokedEvent(GROUP, ACTION_INVOKED_EVENT_ID, ADDITIONAL)

    @JvmStatic
    fun registerActionInvokedEvent(group: EventLogGroup, eventId: String, vararg extraFields: EventField<*>): VarargEventId {
      return group.registerVarargEvent(
        eventId,
        EventFields.PluginInfoFromInstance,
        EventFields.InputEvent,
        EventFields.ActionPlace,
        EventFields.CurrentFile,
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
      EventFields.String("action_id").withCustomRule("action"),
      EventFields.InputEvent)
  }
}