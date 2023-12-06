// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls

object ActionsEventLogGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  const val ACTION_FINISHED_EVENT_ID: String = "action.finished"

  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("actions", 74)

  @JvmField
  val ACTION_ID: PrimitiveEventField<String?> = ActioIdEventField("action_id")

  @Deprecated("Introduced only for MLSE. Do not use.",
              ReplaceWith("com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup.ACTION_ID"))
  @Suppress("FunctionName")
  @JvmStatic
  fun ActioID(@NonNls name: String): PrimitiveEventField<String?> = ActioIdEventField(name)

  private data class ActioIdEventField(override val name: String) : PrimitiveEventField<String?>() {

    override fun addData(fuData: FeatureUsageData, value: String?) {
      if (value == null) {
        return
      }

      fuData.addData(name, StringUtil.substringBeforeLast(value, "$\$Lambda$", true))
    }

    override val validationRule: List<String>
      get() = listOf("{util#${CustomValidationRule.getCustomValidationRuleInstance(ActionRuleValidator::class.java).ruleId}}")
  }

  @JvmField
  val ACTION_CLASS: EventField<Class<*>?> = EventFields.Class("class")

  @JvmField
  val ACTION_PARENT: EventField<Class<*>?> = EventFields.Class("parent")

  @JvmField
  val TOGGLE_ACTION: BooleanEventField = EventFields.Boolean("enable")

  @JvmField
  val CONTEXT_MENU: BooleanEventField = EventFields.Boolean("context_menu")

  @JvmField
  val IS_SUBMENU: BooleanEventField = EventFields.Boolean("isSubmenu")

  @JvmField
  val DUMB_START: BooleanEventField = EventFields.Boolean("dumb_start")

  @JvmField
  val DUMB: BooleanEventField = EventFields.Boolean("dumb")

  @JvmField
  val RESULT_TYPE: StringEventField = EventFields.String("type", arrayListOf("ignored", "performed", "failed", "unknown"))

  @JvmField
  val ERROR: ClassEventField = EventFields.Class("error")

  @JvmField
  val RESULT: ObjectEventField = ObjectEventField("result", RESULT_TYPE, ERROR)

  @JvmField
  val ADDITIONAL: ObjectEventField = EventFields.createAdditionalDataField(GROUP.id, ACTION_FINISHED_EVENT_ID)

  @JvmField
  val ACTION_FINISHED: VarargEventId = registerActionEvent(
    GROUP, ACTION_FINISHED_EVENT_ID, EventFields.StartTime, ADDITIONAL, EventFields.Language, EventFields.DurationMs, DUMB_START, RESULT
  )

  @JvmField
  val ACTION_UPDATED: VarargEventId = GROUP.registerVarargEvent("action.updated", EventFields.PluginInfo,
                                                                ACTION_ID, ACTION_CLASS, ACTION_PARENT,
                                                                EventFields.Language, EventFields.DurationMs)

  @JvmField
  val ACTION_GROUP_EXPANDED: VarargEventId = GROUP.registerVarargEvent("action.group.expanded", EventFields.PluginInfo,
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
  val CUSTOM_ACTION_INVOKED: EventId2<String?, FusInputEvent?> = GROUP.registerEvent(
    "custom.action.invoked",
    ACTION_ID,
    EventFields.InputEvent)
}