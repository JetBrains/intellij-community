// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.UtilValidationRule;
import org.jetbrains.annotations.NotNull;

final class RejectingUtilRuleProducer extends UtilRuleProducer {
  @Override
  public UtilValidationRule createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData) {
    return (s, c) -> ValidationResultType.REJECTED;
  }
}
