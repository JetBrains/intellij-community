// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.isDevelopedByJetBrains
import com.intellij.openapi.ui.DialogWrapper

private const val DIALOGS = "ui.dialogs"
private const val DIALOGS_DEFAULT = "third.party"

class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  private val SELECT_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "select")
  private val APPLY_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "apply")
  private val RESET_CONFIGURABLE_DATA = FeatureUsageData().addData("type", "reset")

  private val SHOW_DIALOG_DATA = FeatureUsageData().addData("type", "show")
  private val CLOSE_OK_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.OK_EXIT_CODE)
  private val CLOSE_CANCEL_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.CANCEL_EXIT_CODE)
  private val CLOSE_CUSTOM_DIALOG_DATA = FeatureUsageData().addData("type", "close").addData("code", DialogWrapper.NEXT_USER_EXIT_CODE)

  override fun logSelectConfigurable(name: String, context: Class<*>) {
  }

  override fun logApplyConfigurable(name: String, context: Class<*>) {
  }

  override fun logResetConfigurable(name: String, context: Class<*>) {
  }

  override fun logShowDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, DIALOGS_DEFAULT)
      FUCounterUsageLogger.getInstance().logEvent(DIALOGS, report, SHOW_DIALOG_DATA)
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, DIALOGS_DEFAULT)
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        FUCounterUsageLogger.getInstance().logEvent(DIALOGS, report, CLOSE_OK_DIALOG_DATA)
      }
      else if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        FUCounterUsageLogger.getInstance().logEvent(DIALOGS, report, CLOSE_CANCEL_DIALOG_DATA)
      }
      else {
        FUCounterUsageLogger.getInstance().logEvent(DIALOGS, report, CLOSE_CUSTOM_DIALOG_DATA)
      }
    }
  }

  private fun toReport(context: Class<*>, name: String, defaultValue: String): String {
    val pluginId = PluginManagerCore.getPluginByClassName(context.name)
    return if (isDevelopedByJetBrains(pluginId)) name else defaultValue
  }
}