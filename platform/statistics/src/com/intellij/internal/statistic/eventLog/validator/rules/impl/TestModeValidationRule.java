// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

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
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    return myTestMode ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
