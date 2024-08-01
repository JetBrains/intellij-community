// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

internal object DialogsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.dialogs", 61)

  val EXIT_CODE: PrimitiveEventField<Int> = object: PrimitiveEventField<Int>() {
    override val name: String = "code"

    override val validationRule: List<String>
      get() = listOf("{enum:0|1|2}")

    override fun addData(fuData: FeatureUsageData, value: Int) {
      val toReport = getExitCodeToReport(value)
      fuData.addData(name, toReport)
    }

    private fun getExitCodeToReport(exitCode: Int): Int {
      if (exitCode == DialogWrapper.OK_EXIT_CODE || exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        return exitCode
      }
      return DialogWrapper.NEXT_USER_EXIT_CODE
    }
  }

  val DIALOG_CLASS: ClassEventField = EventFields.Class("dialog_class")

  val SHOW: VarargEventId = GROUP.registerVarargEvent("show", DIALOG_CLASS, EventFields.PluginInfo)
  val CLOSE: VarargEventId = GROUP.registerVarargEvent("close", DIALOG_CLASS, EXIT_CODE, EventFields.PluginInfo)
  val HELP: VarargEventId = GROUP.registerVarargEvent("help.clicked", DIALOG_CLASS, EventFields.PluginInfo)

  override fun getGroup(): EventLogGroup = GROUP
}

internal object SettingsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.settings", 62)

  private val CONFIGURABLE_CLASS = EventFields.Class("configurable")
  val SELECT: EventId3<Class<*>?, Boolean, Long> = GROUP.registerEvent("select",
                                                                       CONFIGURABLE_CLASS,
                                                                       EventFields.Boolean("loaded_from_cache"),
                                                                       EventFields.DurationMs)
  val APPLY: EventId1<Class<*>?> = GROUP.registerEvent("apply", CONFIGURABLE_CLASS)
  val RESET: EventId1<Class<*>?> = GROUP.registerEvent("reset", CONFIGURABLE_CLASS)
  @JvmField
  val SEARCH: EventId3<Class<*>?, Int, Int> = GROUP.registerEvent("search", CONFIGURABLE_CLASS,
                                                                  EventFields.Int("hits"),
                                                                  EventFields.Int("characters"))
  val ADVANDED_SETTINGS_SEARCH: EventId3<Int, Int, Boolean> = GROUP.registerEvent("advanced.settings.search",
                                                                                  EventFields.Int("hits"),
                                                                                  EventFields.Int("characters"),
                                                                                  EventFields.Boolean("modifiedOnly"))

  override fun getGroup(): EventLogGroup = GROUP
}

internal class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable, loadedFromCache: Boolean, loadTimeMs: Long) {
    if (FeatureUsageLogger.isEnabled()) {
      val wrapper = configurable as? ConfigurableWrapper
      SettingsCounterUsagesCollector.SELECT.log(
        wrapper?.project,
        (wrapper?.configurable ?: configurable)::class.java,
        loadedFromCache,
        loadTimeMs
      )
    }
  }

  override fun logApplyConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.APPLY)
    }
  }

  override fun logResetConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.RESET)
    }
  }

  private fun logSettingsEvent(configurable: Configurable, event: EventId1<Class<*>>) {
    val wrapper = configurable as? ConfigurableWrapper
    event.log(wrapper?.project, (wrapper?.configurable ?: configurable)::class.java)
  }

  override fun logShowDialog(clazz: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.SHOW.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz))
    }
  }

  override fun logCloseDialog(clazz: Class<*>, exitCode: Int) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.CLOSE.log(
        DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
        DialogsCounterUsagesCollector.EXIT_CODE.with(exitCode)
      )
    }
  }

  override fun logClickOnHelpDialog(clazz: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.HELP.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz))
    }
  }
}