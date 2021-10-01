// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ActionsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    const val ACTION_FINISHED_EVENT_ID = "action.finished"

    @JvmField
    val GROUP = EventLogGroup("actions", 66)

    @JvmField
    val START_TIME = EventFields.Long("start_time")

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
    val DUMB_START = EventFields.Boolean("dumb_start")

    @JvmField
    val DUMB = EventFields.Boolean("dumb")

    @JvmField
    val RESULT_TYPE = EventFields.String("type", arrayListOf("ignored", "performed", "failed", "unknown"))

    @JvmField
    val ERROR = EventFields.Class("error")

    @JvmField
    val RESULT = ObjectEventField("result", RESULT_TYPE, ERROR)

    @JvmField
    val ADDITIONAL = EventFields.createAdditionalDataField(GROUP.id, ACTION_FINISHED_EVENT_ID)

    @JvmField
    val ACTION_FINISHED = registerActionEvent(
      GROUP, ACTION_FINISHED_EVENT_ID, START_TIME, ADDITIONAL, EventFields.Language, EventFields.DurationMs, DUMB_START, RESULT
    )

    @JvmStatic
    fun registerActionEvent(group: EventLogGroup, eventId: String, vararg extraFields: EventField<*>): VarargEventId {
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