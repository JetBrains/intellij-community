// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

class PluginInfoValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "plugin_info"

  override fun acceptRuleId(ruleId: String?): Boolean = ruleId in acceptedRules

  override fun doValidate(data: String, context: IEventContext): ValidationResultType = acceptWhenReportedByPluginFromPluginRepository(context)

  private val acceptedRules = hashSetOf(
    "plugin_info", "project_type", "framework", "gutter_icon", "editor_notification_panel_key", "plugin_version"
  )
}
