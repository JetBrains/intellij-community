// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface CustomValidationRuleFactory {
  ExtensionPointName<CustomValidationRuleFactory> EP_NAME = ExtensionPointName.create("com.intellij.statistics.validation.customValidationRuleFactory");

  CustomValidationRule createValidator(EventGroupContextData contextData);

  String getRuleId();

  Class<?> getRuleClass();
}
