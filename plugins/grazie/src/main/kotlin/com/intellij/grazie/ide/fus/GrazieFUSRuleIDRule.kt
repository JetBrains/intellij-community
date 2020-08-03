// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.fus

import com.intellij.grazie.jlanguage.LangTool
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class GrazieFUSRuleIDRule : CustomValidationRule() {
  override fun doValidate(data: String, context: EventContext) = if (data in LangTool.allRules) {
    ValidationResultType.ACCEPTED
  }
  else {
    ValidationResultType.REJECTED
  }

  override fun acceptRuleId(ruleId: String?) = ruleId == "grazie_rule_id"
}
