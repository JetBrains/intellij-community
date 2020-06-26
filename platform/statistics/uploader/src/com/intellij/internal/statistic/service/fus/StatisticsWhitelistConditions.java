// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class StatisticsWhitelistConditions {
  private final Map<String, StatisticsWhitelistGroupConditions> myGroups;

  private StatisticsWhitelistConditions(@NotNull Map<String, StatisticsWhitelistGroupConditions> groups) {
    myGroups = groups;
  }

  @NotNull
  public static StatisticsWhitelistConditions create(@NotNull Map<String, StatisticsWhitelistGroupConditions> groups) {
    return new StatisticsWhitelistConditions(groups);
  }

  @NotNull
  public static StatisticsWhitelistConditions empty() {
    return new StatisticsWhitelistConditions(Collections.emptyMap());
  }

  public boolean accepts(@NotNull String groupId, @Nullable String version, @NotNull String build) {
    if (!myGroups.containsKey(groupId)) {
      return false;
    }

    int parsedVersion = tryToParse(version);
    if (parsedVersion < 0) {
      return false;
    }
    StatisticsWhitelistGroupConditions condition = myGroups.get(groupId);
    return condition.accepts(EventLogBuild.fromString(build), parsedVersion);
  }

  public int getSize() {
    return myGroups.size();
  }

  public boolean isEmpty() {
    return myGroups.isEmpty();
  }

  private static int tryToParse(@Nullable String value) {
    try {
      if (value != null) {
        return Integer.parseInt(value.trim());
      }
    }
    catch (NumberFormatException e) {
      // ignore
    }
    return -1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StatisticsWhitelistConditions whitelist = (StatisticsWhitelistConditions)o;
    return Objects.equals(myGroups, whitelist.myGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroups);
  }
}
