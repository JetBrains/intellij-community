// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRuleFactory;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.UtilValidationRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CustomRuleProducer extends UtilRuleProducer {
  private final boolean myTestMode;

  public CustomRuleProducer(@NotNull String recorderId) {
    myTestMode = StatisticsRecorderUtil.isTestModeEnabled(recorderId);
  }

  @Override
  public @Nullable UtilValidationRule createValidationRule(@NotNull String value,
                                                           @NotNull EventGroupContextData contextData) {
    for (CustomValidationRule extension : CustomValidationRule.EP_NAME.getExtensionList()) {
      if (isAcceptedRule(extension) && extension.acceptRuleId(value)) {
        return extension;
      }
    }

    for (CustomValidationRuleFactory extension : CustomValidationRuleFactory.EP_NAME.getExtensions()) {
      if (isDevelopedByJetBrains(extension.getClass()) && value.equals(extension.getRuleId())) {
        return extension.createValidator(contextData);
      }
    }

    return null;
  }

  private boolean isAcceptedRule(FUSRule extension) {
    if (extension instanceof TestModeValidationRule && !myTestMode) return false;
    return isDevelopedByJetBrains(extension.getClass());
  }

  private static boolean isDevelopedByJetBrains(Class<?> clazz) {
    return ApplicationManager.getApplication().isUnitTestMode() ||
           PluginInfoDetectorKt.getPluginInfo(clazz).isDevelopedByJetBrains();
  }
}
