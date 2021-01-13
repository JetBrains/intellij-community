// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.util.StringUtil;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule;
import org.jetbrains.annotations.NotNull;

final class EnumRuleProducer implements ValidationRuleProducer<EnumValidationRule> {
  private static final String ENUM_PREFIX = "enum:";          // enum:A|B|C
  private static final String ENUM_SEPARATOR = "|";

  @Override
  public EnumValidationRule createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData) {
    return new EnumValidationRule(StringUtil.split(value, ENUM_SEPARATOR, true, false));
  }

  @Override
  @NotNull
  public String getPrefix() {
    return ENUM_PREFIX;
  }
}
