// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogHostType
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachToProcessView
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachViewType

internal object AttachDialogStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("debugger.attach.dialog", 4)

  private val IS_MAIN_ACTION_EVENT_FIELD = EventFields.Boolean("isMainAction")
  private val VIEW_TYPE_EVENT_FIELD = EventFields.Enum<AttachViewType>("viewType")
  private val DEBUGGERS_FILTER_SET_EVENT_FIELD = EventFields.Boolean("debuggersFilterSet")
  private val SEARCH_FIELD_USED_EVENT_FIELD = EventFields.Boolean("searchFieldUsed")
  private val DEBUGGER_NAME_EVENT_FIELD = EventFields.Class("selectedDebugger")

  private val HOST_SWITCHED = GROUP.registerEvent("host.switched", EventFields.Class("hostType"))
  private val VIEW_SWITCHED = GROUP.registerEvent("view.switched", VIEW_TYPE_EVENT_FIELD)
  private val SEARCH_FIELD_USED = GROUP.registerEvent("search.filter.used")
  private val DEBUGGERS_FILTER_SET = GROUP.registerEvent("debuggers.filter.set")
  private val ATTACH_BUTTON_PRESSED = GROUP.registerVarargEvent("attach.button.pressed",
                                                                DEBUGGER_NAME_EVENT_FIELD,
                                                                IS_MAIN_ACTION_EVENT_FIELD,
                                                                VIEW_TYPE_EVENT_FIELD,
                                                                DEBUGGERS_FILTER_SET_EVENT_FIELD,
                                                                SEARCH_FIELD_USED_EVENT_FIELD)

  fun hostSwitched(view: AttachToProcessView) = HOST_SWITCHED.log(view.getHostType().javaClass)
  fun viewSwitched(viewType: AttachViewType) = VIEW_SWITCHED.log(viewType)
  fun searchFieldUsed() = SEARCH_FIELD_USED.log()
  fun debuggersFilterSet() = DEBUGGERS_FILTER_SET.log()
  fun attachButtonPressed(
    debuggerClass: Class<*>,
    isMainAction: Boolean,
    selectedViewType: AttachViewType,
    debuggersFilterSet: Boolean,
    searchFieldIsUsed: Boolean) = ATTACH_BUTTON_PRESSED.log(
    DEBUGGER_NAME_EVENT_FIELD.with(debuggerClass),
    IS_MAIN_ACTION_EVENT_FIELD.with(isMainAction),
    VIEW_TYPE_EVENT_FIELD.with(selectedViewType),
    DEBUGGERS_FILTER_SET_EVENT_FIELD.with(debuggersFilterSet),
    SEARCH_FIELD_USED_EVENT_FIELD.with(searchFieldIsUsed))
}