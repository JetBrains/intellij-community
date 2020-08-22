// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

class PluginInfoValidationRule : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?) = ruleId in acceptedRules

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return acceptWhenReportedByPluginFromPluginRepository(context)
  }

  companion object {
    private val acceptedRules = hashSetOf("plugin_info", "project_type", "framework", "gutter_icon", "editor_notification_panel_key", "plugin_version")
  }
}
