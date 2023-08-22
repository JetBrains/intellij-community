// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
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

  @NotNull
  @Override
  public String getRuleId() {
    return ruleId;
  }

  private boolean isAllowed(@NotNull String value) {
    return storage.getItems().contains(value);
  }

  @NotNull
  @Override
  final protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data) || isAllowed(data)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }
}
