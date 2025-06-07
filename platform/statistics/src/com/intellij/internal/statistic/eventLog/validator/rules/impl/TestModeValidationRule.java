// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED;
import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.REJECTED;

public final class TestModeValidationRule extends CustomValidationRule {
  private final boolean myTestMode;

  public TestModeValidationRule() {
    myTestMode = StatisticsRecorderUtil.isAnyTestModeEnabled();
  }

  @Override
  public @NotNull String getRuleId() {
    return "fus_test_mode";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    return myTestMode ? ACCEPTED : REJECTED;
  }
}
