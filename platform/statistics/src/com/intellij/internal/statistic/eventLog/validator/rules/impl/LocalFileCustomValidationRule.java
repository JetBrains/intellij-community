// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public abstract class LocalFileCustomValidationRule extends CustomValidationRule {
  private final AllowedItemsResourceWeakRefStorage storage;
  private final String ruleId;

  protected LocalFileCustomValidationRule(@NotNull String ruleId, @NotNull Class<?> resource, @NotNull String path) {
    this.ruleId = ruleId;
    storage = new AllowedItemsResourceWeakRefStorage(resource, path);
  }

  protected LocalFileCustomValidationRule(@NotNull String ruleId, @NotNull AllowedItemsResourceWeakRefStorage storage) {
    this.ruleId = ruleId;
    this.storage = storage;
  }

  @Override
  public @NotNull String getRuleId() {
    return ruleId;
  }

  private boolean isAllowed(@NotNull String value) {
    return storage.getItems().contains(value);
  }

  @Override
  protected final @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    return isThirdPartyValue(data) || isAllowed(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
