// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ActionsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    const val ACTION_FINISHED_EVENT_ID = "action.finished"

    @JvmField
    val GROUP = EventLogGroup("actions", 70)

    @JvmField
    val ACTION_ID = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)

    @JvmField
    val ACTION_CLASS = EventFields.StringValidatedByCustomRule("class", ClassNameRuleValidator::class.java)

    @JvmField
    val ACTION_PARENT = EventFields.StringValidatedByCustomRule("parent", ClassNameRuleValidator::class.java)

    @JvmField
    val TOGGLE_ACTION = EventFields.Boolean("enable")

    @JvmField
    val CONTEXT_MENU = EventFields.Boolean("context_menu")

    @JvmField
    val IS_SUBMENU = EventFields.Boolean("isSubmenu")

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
      GROUP, ACTION_FINISHED_EVENT_ID, EventFields.StartTime, ADDITIONAL, EventFields.Language, EventFields.DurationMs, DUMB_START, RESULT
    )

    @JvmField
    val ACTION_UPDATED = GROUP.registerVarargEvent("action.updated", EventFields.PluginInfo,
                                                   ACTION_ID, ACTION_CLASS, ACTION_PARENT,
                                                   EventFields.Language, EventFields.DurationMs)

    @JvmField
    val ACTION_GROUP_EXPANDED = GROUP.registerVarargEvent("action.group.expanded", EventFields.PluginInfo,
                                                          ACTION_ID, ACTION_CLASS, ACTION_PARENT,
                                                          EventFields.ActionPlace, IS_SUBMENU, EventFields.Size,
                                                          EventFields.Language, EventFields.DurationMs)


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
      EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java),
      EventFields.InputEvent)
  }
}