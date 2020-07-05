// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.getPluginInfo

internal class MethodNameRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = "method_name" == ruleId

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (isThirdPartyValue(data)) {
      return ValidationResultType.ACCEPTED
    }

    val lastDotIndex = data.lastIndexOf(".")
    if (lastDotIndex == -1) {
      return ValidationResultType.REJECTED
    }

    val className = data.substring(0, lastDotIndex)
    val info = getPluginInfo(className)

    if (info.type === PluginType.UNKNOWN) {
      // if we can't detect a plugin then probably it's not a class name
      return ValidationResultType.REJECTED
    }

    return if (info.isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}