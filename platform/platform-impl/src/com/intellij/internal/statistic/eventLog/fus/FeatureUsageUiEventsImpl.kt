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
  private val CLOSE_OK_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.OK_EXIT_CODE)
  private val CLOSE_CANCEL_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.CANCEL_EXIT_CODE)
  private val CLOSE_CUSTOM_DIALOG_DATA = FeatureUsageData().addData("code", DialogWrapper.NEXT_USER_EXIT_CODE)

  override fun logSelectConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, "select")
    }
  }

  override fun logApplyConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, "apply")
    }
  }

  override fun logResetConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.isEnabled()) {
      logSettingsEvent(configurable, "reset")
    }
  }

  private fun logSettingsEvent(configurable: Configurable, event: String) {
    val base: Any? = if (configurable is ConfigurableWrapper) configurable.configurable else configurable
    base?.let {
      val data = FeatureUsageData().addData("configurable", base::class.java.name)
      FUCounterUsageLogger.getInstance().logEvent(SETTINGS, event, data)
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

  private fun getDialogCloseData(exitCode: Int): FeatureUsageData {
    return when (exitCode) {
      DialogWrapper.OK_EXIT_CODE -> CLOSE_OK_DIALOG_DATA
      DialogWrapper.CANCEL_EXIT_CODE -> CLOSE_CANCEL_DIALOG_DATA
      else -> CLOSE_CUSTOM_DIALOG_DATA
    }
  }

  internal fun FeatureUsageData.addDialogClass(name: String) = this.addData("dialog_class", name)
}