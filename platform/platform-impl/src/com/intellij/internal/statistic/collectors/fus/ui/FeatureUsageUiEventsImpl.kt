// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

class DialogsCounterUsagesCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ui.dialogs", 59)

    val EXIT_CODE = object: PrimitiveEventField<Int>() {
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

    val DIALOG_CLASS = EventFields.StringValidatedByCustomRule("dialog_class", "dialog_class")

    val SHOW = GROUP.registerVarargEvent("show", DIALOG_CLASS, EventFields.PluginInfo)
    val CLOSE = GROUP.registerVarargEvent("close", DIALOG_CLASS, EXIT_CODE, EventFields.PluginInfo)
    val HELP = GROUP.registerVarargEvent("help.clicked", DIALOG_CLASS, EventFields.PluginInfo)
  }

  override fun getGroup(): EventLogGroup = GROUP
}

class SettingsCounterUsagesCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ui.settings", 61)

    val CONFIGURABLE_CLASS = EventFields.Class("configurable")
    val SELECT = GROUP.registerEvent("select", CONFIGURABLE_CLASS)
    val APPLY = GROUP.registerEvent("apply", CONFIGURABLE_CLASS)
    val RESET = GROUP.registerEvent("reset", CONFIGURABLE_CLASS)
    @JvmField
    val SEARCH = GROUP.registerEvent("search", CONFIGURABLE_CLASS,
                                     EventFields.Int("hits"),
                                     EventFields.Int("characters"))
    val ADVANDED_SETTINGS_SEARCH = GROUP.registerEvent("advanced.settings.search",
                                                       EventFields.Int("hits"),
                                                       EventFields.Int("characters"),
                                                       EventFields.Boolean("modifiedOnly"))
  }

  override fun getGroup() = GROUP
}

class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.SELECT)
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

  override fun logShowDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.SHOW.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(name))
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.CLOSE.log(
        DialogsCounterUsagesCollector.DIALOG_CLASS.with(name),
        DialogsCounterUsagesCollector.EXIT_CODE.with(exitCode)
      )
    }
  }

  override fun logClickOnHelpDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.HELP.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(name))
    }
  }
}