// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public class PluginIdRuleValidator extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return "plugin";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    return isPluginFromPluginRepository(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
