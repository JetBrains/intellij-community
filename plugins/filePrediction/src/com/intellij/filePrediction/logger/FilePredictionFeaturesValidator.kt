// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.FilePredictionEventFieldEncoder
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class FilePredictionFeaturesValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = ruleId == "file_features"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    data.split(';', ',').forEach {
      if (!isFeatureValid(it)) return ValidationResultType.REJECTED
    }
    return ValidationResultType.ACCEPTED
  }

  private fun isFeatureValid(feature: String): Boolean {
    if (feature.isEmpty() || feature.toDoubleOrNull() != null || isThirdPartyValue(feature) || "UNKNOWN" == feature) {
      return true
    }

    return FilePredictionEventFieldEncoder.isFileTypeValid(feature) == ValidationResultType.ACCEPTED
  }
}