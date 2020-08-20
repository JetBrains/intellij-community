// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.EnumValidationRule;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.RegexpValidationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventGroupContextData {
  public static final EventGroupContextData EMPTY = create(null, null, null, null);

  @Nullable private final Map<String, Set<String>> myEnums;
  @Nullable private final Map<String, Set<String>> myGlobalEnums;
  @Nullable private final Map<String, String> myRegexps;
  @Nullable private final Map<String, String> myGlobalRegexps;
  private static final Map<String, RegexpValidationRule> ourGlobalRegexpRules = new HashMap<>();
  private static final Map<String, EnumValidationRule> ourGlobalEnumRules = new HashMap<>();

  public EventGroupContextData(@Nullable Map<String, Set<String>> enums,
                               @Nullable Map<String, Set<String>> globalEnums,
                               @Nullable Map<String, String> regexps,
                               @Nullable Map<String, String> globalRegexps) {
    myEnums = enums;
    myGlobalEnums = globalEnums;
    myRegexps = regexps;
    myGlobalRegexps = globalRegexps;
  }

  public static EventGroupContextData create(@Nullable Map<String, Set<String>> enums,
                                             @Nullable Map<String, Set<String>> globalEnums,
                                             @Nullable Map<String, String> regexps,
                                             @Nullable Map<String, String> globalRegexps) {
    return new EventGroupContextData(enums, globalEnums, regexps, globalRegexps);
  }

  @NotNull
  public EnumValidationRule getEnumValidationRule(@NotNull String enumRef) {
    if (myEnums != null) {
      Set<String> values = myEnums.get(enumRef);
      if (values != null) return new EnumValidationRule(values);
    }
    if (myGlobalEnums != null) {
      Set<String> globalEnum = myGlobalEnums.get(enumRef);
      if (globalEnum != null) {
        String cashKey = globalEnum.stream().sorted().collect(Collectors.joining(";"));
        return ourGlobalEnumRules.computeIfAbsent(cashKey, id -> {
          return new EnumValidationRule(globalEnum);
        });
      }
    }
    return new EnumValidationRule(null);
  }

  @NotNull
  public RegexpValidationRule getRegexpValidationRule(@NotNull String regexpRef) {
    if (myRegexps != null) {
      String regexp = myRegexps.get(regexpRef);
      if (regexp != null) return new RegexpValidationRule(regexp);
    }
    if (myGlobalRegexps != null) {
      String globalRegexp = myGlobalRegexps.get(regexpRef);
      if (globalRegexp != null) {
        return ourGlobalRegexpRules.computeIfAbsent(globalRegexp, id -> {
          return new RegexpValidationRule(globalRegexp);
        });
      }
    }
    return new RegexpValidationRule(null);
  }
}
