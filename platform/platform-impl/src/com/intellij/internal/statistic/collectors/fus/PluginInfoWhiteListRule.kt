// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule

class PluginInfoWhiteListRule : CustomWhiteListRule() {
  override fun acceptRuleId(ruleId: String?) =
    ruleId == "project_type" || ruleId == "framework" || ruleId == "gutter_icon"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return acceptWhenReportedByPluginFromPluginRepository(context)
  }
}
