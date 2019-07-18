// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogWrapper

private const val SETTINGS = "ui.settings"
private const val DIALOGS = "ui.dialogs"

class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  private val SELECT_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "select")
  private val APPLY_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "apply")
  private val RESET_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "reset")

  private val SHOW_DIALOG_DATA = FeatureUsageData().addData("type", "show")
  private val CLOSE_OK_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.OK_EXIT_CODE)
  private val CLOSE_CANCEL_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.CANCEL_EXIT_CODE)
  private val CLOSE_CUSTOM_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.NEXT_USER_EXIT_CODE)

  override fun logSelectConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, SELECT_CONFIGURABLE_DATA)
    }
  }

  override fun logApplyConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, APPLY_CONFIGURABLE_DATA)
    }
  }

  override fun logResetConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, RESET_CONFIGURABLE_DATA)
    }
  }

  private fun logSettingsEvent(configurable: Configurable, data: FeatureUsageData) {
    val base: Any? = if (configurable is ConfigurableWrapper) configurable.configurable else configurable
    base?.let {
      FUCounterUsageLogger.getInstance().logEvent(SETTINGS, base::class.java.name, data)
    }
  }

  override fun logShowDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, name, SHOW_DIALOG_DATA)
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val data = when (exitCode) {
        DialogWrapper.OK_EXIT_CODE -> CLOSE_OK_DIALOG_DATA
        DialogWrapper.CANCEL_EXIT_CODE -> CLOSE_CANCEL_DIALOG_DATA
        else -> CLOSE_CUSTOM_DIALOG_DATA
      }
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, name, data)
    }
  }
}