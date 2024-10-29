// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Please do not use directly.
 * <br/>
 * Use {@link ActionsEventLogGroup#ACTION_ID} instead.
 */
@ApiStatus.Internal
public final class ActionRuleValidator extends ClassNameRuleValidator {
  @Override
  public @NotNull String getRuleId() {
    return "action";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (ActionsCollectorImpl.canReportActionId(data)) {
      return ValidationResultType.ACCEPTED;
    }
    return super.doValidate(data, context);
  }
}
