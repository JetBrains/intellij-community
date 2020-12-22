// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BooleanRuleProducer implements ValidationRuleProducer<FUSRule> {
  private static final String RULE_PREFIX = "rule:";          // rule:TRUE , rule:FALSE
  @Override
  @Nullable
  public FUSRule createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData) {
    if ("TRUE".equals(value)) return FUSRule.TRUE;
    if ("FALSE".equals(value)) return FUSRule.FALSE;
    return null;
  }

  @Override
  @NotNull
  public String getPrefix() {
    return RULE_PREFIX;
  }
}
