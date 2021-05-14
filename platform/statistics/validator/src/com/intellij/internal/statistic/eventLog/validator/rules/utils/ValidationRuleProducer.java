// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface ValidationRuleProducer<T extends FUSRule> {
  /**
   * @param value       validation rule string without prefix and braces (e.g "foo|bar" for {enum:foo|bar})
   */
  @Nullable T createValidationRule(@NotNull String value, @NotNull EventGroupContextData contextData);

  /**
   * Returns the prefix of the validation rule that can be created by this producer.
   * E.g `enum:` for enum validation rule.
   */
  @NotNull String getPrefix();
}
