// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.getPluginInfo
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

class MethodNameRuleValidator : CustomValidationRule() {
  override fun getRuleId(): String = "method_name"

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
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
      // if we can't detect a plugin, then probably it's not a class name
      return ValidationResultType.REJECTED
    }

    return if (info.isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}
