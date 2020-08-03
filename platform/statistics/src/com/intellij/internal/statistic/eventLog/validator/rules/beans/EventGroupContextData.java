// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.beans;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class EventGroupContextData {
  public static final EventGroupContextData EMPTY = create(null, null, null, null);

  @Nullable private final Map<String, Set<String>> myEnums;
  @Nullable private final Map<String, Set<String>> myGlobalEnums;
  @Nullable private final Map<String, String> myRegexps;
  @Nullable private final Map<String, String> myGlobalRegexps;

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

  @Nullable
  public Set<String> getEnum(@NotNull String enumRef) {
    if (myEnums != null) {
      Set<String> values = myEnums.get(enumRef);
      if (values != null) return values;
    }
    return myGlobalEnums != null ? myGlobalEnums.get(enumRef) : null;
  }

  @Nullable
  public String getRegexp(@NotNull String regexpRef) {
    if (myRegexps != null) {
      String regexp = myRegexps.get(regexpRef);
      if (regexp != null) return regexp;
    }
    return myGlobalRegexps != null ? myGlobalRegexps.get(regexpRef) : null;
  }
}
