// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public abstract class LocalEnumCustomValidationRule extends CustomValidationRule {
  private final String myRule;
  private final Class<? extends Enum<?>> myEnumClass;

  public LocalEnumCustomValidationRule(@NotNull String rule, @NotNull Class<? extends Enum<?>> enumClass) {
    myRule = rule;
    myEnumClass = enumClass;
  }

  @Override
  public @NotNull String getRuleId() {
    return myRule;
  }

  @Override
  protected final @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    for (var constant : myEnumClass.getEnumConstants()) {
      if (data.equals(constant.name())) {
        return ValidationResultType.ACCEPTED;
      }
    }
    return ValidationResultType.REJECTED;
  }
}
