// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class TipIdValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "tip_info"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val info = context.getPayload(PLUGIN_INFO)
    return if (info?.isSafeToReport() == true) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}
