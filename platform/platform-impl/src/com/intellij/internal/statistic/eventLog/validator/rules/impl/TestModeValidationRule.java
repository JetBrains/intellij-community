// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED;
import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.REJECTED;

public class TestModeValidationRule extends CustomUtilsWhiteListRule {
  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "fus_test_mode".equals(ruleId);
  }

  @NotNull
  @Override
  protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if(!ApplicationManager.getApplication().isInternal()) return REJECTED;

    return "true".equals(System.getProperty("fus.internal.test.mode")) ? ACCEPTED : REJECTED;
  }
}
