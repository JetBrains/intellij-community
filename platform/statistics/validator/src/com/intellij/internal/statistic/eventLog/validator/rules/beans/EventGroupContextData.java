// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule;
import com.intellij.internal.statistic.eventLog.validator.storage.GlobalRulesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class EventGroupContextData {
  public static final EventGroupContextData EMPTY = new EventGroupContextData(null, null, null);

  @Nullable private final Map<String, Set<String>> myEnums;
  @Nullable private final Map<String, String> myRegexps;
  @Nullable GlobalRulesHolder myGlobalRulesHolder;

  public EventGroupContextData(@Nullable Map<String, Set<String>> enums,
                               @Nullable Map<String, String> regexps,
                               @Nullable GlobalRulesHolder globalRulesHolder) {
    myEnums = enums;
    myRegexps = regexps;
    myGlobalRulesHolder = globalRulesHolder;
  }

  @NotNull
  public EnumValidationRule getEnumValidationRule(@NotNull String enumRef) {
    if (myEnums != null) {
      Set<String> values = myEnums.get(enumRef);
      if (values != null) return new EnumValidationRule(values);
    }
    if (myGlobalRulesHolder != null) {
      EnumValidationRule globalEnum = myGlobalRulesHolder.getEnumValidationRules(enumRef);
      if (globalEnum != null) return globalEnum;
    }
    return new EnumValidationRule(null);
  }

  @NotNull
  public RegexpValidationRule getRegexpValidationRule(@NotNull String regexpRef) {
    if (myRegexps != null) {
      String regexp = myRegexps.get(regexpRef);
      if (regexp != null) return new RegexpValidationRule(regexp);
    }
    if (myGlobalRulesHolder != null) {
      RegexpValidationRule globalRegexp = myGlobalRulesHolder.getRegexpValidationRules(regexpRef);
      if (globalRegexp != null) return globalRegexp;
    }
    return new RegexpValidationRule(null);
  }
}
