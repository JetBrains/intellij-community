// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Please do not use directly; use [ActionsEventLogGroup#ACTION_ID] instead.
@ApiStatus.Internal
public final class ActionRuleValidator extends ClassNameRuleValidator {
  @Override
  public @NotNull String getRuleId() {
    return "action";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    return ActionsCollectorImpl.canReportActionId(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
