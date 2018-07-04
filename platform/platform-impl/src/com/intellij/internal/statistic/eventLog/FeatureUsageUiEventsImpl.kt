// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.isDevelopedByJetBrains
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.containers.ContainerUtil

private const val DIALOGS_ID = "ui.dialogs"
private const val SETTINGS_ID = "ui.settings"
private const val SETTINGS_DEFAULT = "ide.settings.third.party.plugin"
private const val DIALOGS_DEFAULT = "dialog.third.party.plugin"

class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  private val SELECT_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val APPLY_CONFIGURABLE_DATA = HashMap<String, Any>()
  private val RESET_CONFIGURABLE_DATA = HashMap<String, Any>()

  private val SHOW_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_OK_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_CANCEL_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()
  private val CLOSE_CUSTOM_DIALOG_DATA = ContainerUtil.newHashMap<String, Any>()

  init {
    SELECT_CONFIGURABLE_DATA["type"] = "select"
    APPLY_CONFIGURABLE_DATA["type"] = "apply"
    RESET_CONFIGURABLE_DATA["type"] = "reset"

    SHOW_DIALOG_DATA["type"] = "show"
    CLOSE_OK_DIALOG_DATA["type"] = "close"
    CLOSE_OK_DIALOG_DATA["code"] = DialogWrapper.OK_EXIT_CODE
    CLOSE_CANCEL_DIALOG_DATA["type"] = "close"
    CLOSE_CANCEL_DIALOG_DATA["code"] = DialogWrapper.CANCEL_EXIT_CODE
    CLOSE_CUSTOM_DIALOG_DATA["type"] = "close"
    CLOSE_CUSTOM_DIALOG_DATA["code"] = DialogWrapper.NEXT_USER_EXIT_CODE
  }

  override fun logSelectConfigurable(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, SETTINGS_DEFAULT)
      FeatureUsageLogger.log(SETTINGS_ID, report, SELECT_CONFIGURABLE_DATA)
    }
  }

  override fun logApplyConfigurable(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, SETTINGS_DEFAULT)
      FeatureUsageLogger.log(SETTINGS_ID, report, APPLY_CONFIGURABLE_DATA)
    }
  }

  override fun logResetConfigurable(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, SETTINGS_DEFAULT)
      FeatureUsageLogger.log(SETTINGS_ID, report, RESET_CONFIGURABLE_DATA)
    }
  }

  override fun logShowDialog(name: String, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, DIALOGS_DEFAULT)
      FeatureUsageLogger.log(DIALOGS_ID, report, SHOW_DIALOG_DATA)
    }
  }

  override fun logCloseDialog(name: String, exitCode: Int, context: Class<*>) {
    if (FeatureUsageLogger.isEnabled()) {
      val report = toReport(context, name, DIALOGS_DEFAULT)
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        FeatureUsageLogger.log(DIALOGS_ID, report, CLOSE_OK_DIALOG_DATA)
      }
      else if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        FeatureUsageLogger.log(DIALOGS_ID, report, CLOSE_CANCEL_DIALOG_DATA)
      }
      else {
        FeatureUsageLogger.log(DIALOGS_ID, report, CLOSE_CUSTOM_DIALOG_DATA)
      }
    }
  }

  private fun toReport(context: Class<*>, name: String, defaultValue: String): String {
    val pluginId = PluginManagerCore.getPluginByClassName(context.name)
    return if (isDevelopedByJetBrains(pluginId)) name else defaultValue
  }
}