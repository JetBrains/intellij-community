// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.UtilValidationRule;
import org.jetbrains.annotations.NotNull;

public abstract class UtilRuleProducer implements ValidationRuleProducer<UtilValidationRule>{
  @Override
  @NotNull
  public final String getPrefix() {
    return ValidationSimpleRuleFactory.UTIL_PREFIX;
  }
}
