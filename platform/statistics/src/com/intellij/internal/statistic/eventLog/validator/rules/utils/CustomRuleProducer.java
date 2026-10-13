// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRuleFactory;
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

  /**
   * Returns a {@link CustomRuleReference} instead of the rule extension, so the rules do not keep an extension of an unloaded plugin.
   * A factory rule is created as before, because it gets the context of the group.
   */
  @Override
  public @Nullable UtilValidationRule createValidationRule(@NotNull String value,
                                                           @NotNull EventGroupContextData contextData) {
    CustomRuleReference reference = new CustomRuleReference(value, myTestMode);
    if (reference.resolve() != null) {
      return reference;
    }

    for (CustomValidationRuleFactory extension : CustomValidationRuleFactory.EP_NAME.getExtensions()) {
      if (isDevelopedByJetBrains(extension.getClass()) && value.equals(extension.getRuleId())) {
        return extension.createValidator(contextData);
      }
    }

    return reference;
  }

  static boolean isDevelopedByJetBrains(Class<?> clazz) {
    return ApplicationManager.getApplication().isUnitTestMode() ||
           PluginInfoDetectorKt.getPluginInfo(clazz).isDevelopedByJetBrains();
  }
}
