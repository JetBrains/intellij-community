// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated inherit {@link CustomValidationRule} and register "customValidationRule" EP in plugin.xml
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public abstract class CustomWhiteListRule extends PerformanceCareRule implements FUSRule {
  public static final ExtensionPointName<CustomWhiteListRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customWhiteListRule");

  public abstract boolean acceptRuleId(@Nullable String ruleId);
}
