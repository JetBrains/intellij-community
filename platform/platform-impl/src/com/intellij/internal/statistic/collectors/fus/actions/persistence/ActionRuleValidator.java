// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomUtilsWhiteListRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ActionRuleValidator extends CustomUtilsWhiteListRule {
  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "action".equals(ruleId);
  }

  @NotNull
  @Override
  protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (ActionsCollectorImpl.isCustomAllowedAction(data)) return ValidationResultType.ACCEPTED;

    return acceptWhenReportedByJetbrainsPlugin(context);
  }
}
