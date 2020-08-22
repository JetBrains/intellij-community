// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import java.net.URI

class UpdateDialogURLValidationRule : CustomValidationRule() {
  private val jbDomains = listOf("jetbrains.com", "intellij.net", "intellij.com", "kotlinlang.com", "jb.gg")

  override fun acceptRuleId(ruleId: String?) =
    ruleId == "update_dialog_rule_id"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    try {
      return if (jbDomains.any { domain -> URI.create(data).host.endsWith(domain) }) {
        ValidationResultType.ACCEPTED
      }
      else {
        ValidationResultType.REJECTED
      }
    }
    catch (e: Exception) {
      return ValidationResultType.REJECTED
    }
  }
}