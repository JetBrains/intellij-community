// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

private const val DIALOGS = "ui.dialogs"

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
  private val CLOSE_OK_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.OK_EXIT_CODE)
  private val CLOSE_CANCEL_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.CANCEL_EXIT_CODE)
  private val CLOSE_CUSTOM_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.NEXT_USER_EXIT_CODE)

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
      val data = FeatureUsageData().addDialogClass(name)
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, "show", data)
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val data = getDialogCloseData(exitCode).copy().addDialogClass(name)
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, "close", data)
    }
  }

  override fun logClickOnHelpDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val data = FeatureUsageData().addDialogClass(name)
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, "help.clicked", data)
    }
  }

  private fun getDialogCloseData(exitCode: Int): FeatureUsageData {
    return when (exitCode) {
      DialogWrapper.OK_EXIT_CODE -> CLOSE_OK_DIALOG_DATA
      DialogWrapper.CANCEL_EXIT_CODE -> CLOSE_CANCEL_DIALOG_DATA
      else -> CLOSE_CUSTOM_DIALOG_DATA
    }
  }

  internal fun FeatureUsageData.addDialogClass(name: String) = this.addData("dialog_class", name)
}