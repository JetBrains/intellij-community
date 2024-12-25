// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ProductCodeRuleValidator extends CustomValidationRule {

  private static final Set<String> codes = Set.of(
    "IU",
    "IC",
    "PY",
    "PC",
    "WS",
    "GO",
    "CL",
    "RD",
    "RM",
    "RR",
    "AS",
    "QA"
  );

  @Override
  public @NotNull String getRuleId() {
    return "productCode";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    return codes.contains(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
