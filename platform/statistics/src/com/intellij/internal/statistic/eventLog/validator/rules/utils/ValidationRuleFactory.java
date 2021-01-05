// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class ValidationRuleFactory extends ValidationSimpleRuleFactory {
  public ValidationRuleFactory() {
    super(new CustomRuleProducer());
  }

  @Override
  public FUSRule @NotNull [] getRules(@Nullable String key,
                                      @Nullable Set<String> rules,
                                      @NotNull EventGroupContextData contextData) {
    if (FeatureUsageData.Companion.getPlatformDataKeys().contains(key)) {
      return new FUSRule[]{FUSRule.TRUE};
    }
    return super.getRules(key, rules, contextData);
  }
}
