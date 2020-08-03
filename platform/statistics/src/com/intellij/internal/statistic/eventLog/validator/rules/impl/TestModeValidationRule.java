// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED;
import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.REJECTED;

public final class TestModeValidationRule extends CustomValidationRule {
  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "fus_test_mode".equals(ruleId);
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    return isTestModeEnabled() ? ACCEPTED : REJECTED;
  }

  public static boolean isTestModeEnabled() {
    return ApplicationManager.getApplication().isInternal() && "true".equals(System.getProperty("fus.internal.test.mode"));
  }
}
