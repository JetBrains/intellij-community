// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

class DialogsCounterUsagesCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ui.dialogs", 58)

    val EXIT_CODE = EventFields.Int("code")
    val DIALOG_CLASS = EventFields.StringValidatedByCustomRule("dialog_class", "dialog_class")

    val SHOW = GROUP.registerEvent("show", DIALOG_CLASS)
    val CLOSE = GROUP.registerEvent("close", DIALOG_CLASS, EXIT_CODE)
    val HELP = GROUP.registerEvent("help.clicked", DIALOG_CLASS)
  }

  override fun getGroup(): EventLogGroup = GROUP
}

class SettingsCounterUsagesCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ui.settings", 59)

    val CONFIGURABLE_CLASS = EventFields.Class("configurable")
    val SELECT = GROUP.registerEvent("select", CONFIGURABLE_CLASS)
    val APPLY = GROUP.registerEvent("apply", CONFIGURABLE_CLASS)
    val RESET = GROUP.registerEvent("reset", CONFIGURABLE_CLASS)
    @JvmField
    val SEARCH = GROUP.registerEvent("search", CONFIGURABLE_CLASS,
                                     EventFields.Int("hits"),
                                     EventFields.Int("characters"))
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

  private fun logSettingsEvent(configurable: Configurable, event: EventId1<Class<*>?>) {
    val base: Any? = if (configurable is ConfigurableWrapper) configurable.configurable else configurable
    base?.let {
      event.log(if (configurable is ConfigurableWrapper) configurable.project else null, base::class.java)
    }
  }

  override fun logShowDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.SHOW.log(name)
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.CLOSE.log(name, getExitCodeToReport(exitCode))
    }
  }

  override fun logClickOnHelpDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      DialogsCounterUsagesCollector.HELP.log(name)
    }
  }

  private fun getExitCodeToReport(exitCode: Int): Int {
    if (exitCode == DialogWrapper.OK_EXIT_CODE || exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
      return exitCode
    }
    return DialogWrapper.NEXT_USER_EXIT_CODE
  }
}