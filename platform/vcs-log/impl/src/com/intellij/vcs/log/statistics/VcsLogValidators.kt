// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

@Deprecated("Use com.intellij.internal.statistic.eventLog.events.EventFields.String instead")
open class CustomStringsValidationRule(private val id: String, private val values: Collection<String>) : CustomValidationRule() {

  override fun getRuleId(): String = id

  final override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (values.contains(data)) return ValidationResultType.ACCEPTED
    return ValidationResultType.REJECTED
  }
}
