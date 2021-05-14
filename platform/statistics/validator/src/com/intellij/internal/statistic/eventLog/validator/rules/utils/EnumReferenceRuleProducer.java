// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EnumReferenceRuleProducer implements ValidationRuleProducer<EnumValidationRule> {
  private static final String ENUM_REF_PREFIX = "enum#";     //  enum#<ref-id>

  @Override
  public EnumValidationRule createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData) {
    return contextData.getEnumValidationRule(value);
  }

  @Override
  @NotNull
  public String getPrefix() {
    return ENUM_REF_PREFIX;
  }
}
