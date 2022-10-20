package com.intellij.xdebugger.impl.ui.attach.dialog.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogHostType
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachToProcessView
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachViewType

internal class AttachDialogStatisticsCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("debugger.attach.dialog", 1)

    private val HOST_SWITCHED = GROUP.registerEvent("host.switched", EventFields.Enum("hostType", AttachDialogHostType::class.java))
    private val VIEW_SWITCHED = GROUP.registerEvent("view.switched", EventFields.Enum("viewType", AttachViewType::class.java))
    private val SEARCH_FIELD_USED = GROUP.registerEvent("search.filter.used")
    private val DEBUGGERS_FILTER_SET = GROUP.registerEvent("debuggers.filter.set")
    private val ATTACH_BUTTON_PRESSED = GROUP.registerEvent("attach.button.pressed", EventFields.Boolean("isMainAction"))

    fun hostSwitched(view: AttachToProcessView) = HOST_SWITCHED.log(view.getHostType())
    fun viewSwitched(viewType: AttachViewType) = VIEW_SWITCHED.log(viewType)
    fun searchFieldUsed() = SEARCH_FIELD_USED.log()
    fun debuggersFilterSet() = DEBUGGERS_FILTER_SET.log()
    fun attachButtonPressed(isMainAction: Boolean) = ATTACH_BUTTON_PRESSED.log(isMainAction)
  }
}