// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LocalEnumCustomValidationRule extends CustomValidationRule {
  private final String myRule;
  private final Class<? extends Enum> myEnumClass;

  public LocalEnumCustomValidationRule(@NotNull String rule, @NotNull Class<? extends Enum> enumClass) {
    myRule = rule;
    myEnumClass = enumClass;
  }

  @NotNull
  @Override
  public String getRuleId() {
    return myRule;
  }

  @NotNull
  @Override
  final protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    final Enum[] constants = myEnumClass.getEnumConstants();
    for (Enum constant : constants) {
      if (StringUtil.equals(constant.name(), data)) {
        return ValidationResultType.ACCEPTED;
      }
    }
    return ValidationResultType.REJECTED;
  }
}
