// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import org.jetbrains.annotations.NotNull;

public class ActionRuleValidator extends ClassNameRuleValidator {
  @NotNull
  @Override
  public String getRuleId() {
    return "action";
  }

  @NotNull
  @Override
  protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (ActionsCollectorImpl.canReportActionId(data)) {
      return ValidationResultType.ACCEPTED;
    }
    return super.doValidate(data, context);
  }
}
