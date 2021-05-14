// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RegexpRuleProducer implements ValidationRuleProducer<RegexpValidationRule> {
  private static final String REGEXP_PREFIX = "regexp:";      // regexp:\d+

  @Override
  public RegexpValidationRule createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData) {
    return new RegexpValidationRule(value);
  }

  @Override
  @NotNull
  public String getPrefix() {
    return REGEXP_PREFIX;
  }
}
