// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class EventGroupsFilterRules {
  private final Map<String, EventGroupFilterRules> myGroups;

  private EventGroupsFilterRules(@NotNull Map<String, EventGroupFilterRules> groups) {
    myGroups = groups;
  }

  @NotNull
  public static EventGroupsFilterRules create(@NotNull Map<String, EventGroupFilterRules> groups) {
    return new EventGroupsFilterRules(groups);
  }

  @NotNull
  public static EventGroupsFilterRules empty() {
    return new EventGroupsFilterRules(Collections.emptyMap());
  }

  public boolean accepts(@NotNull String groupId, @Nullable String version, @NotNull String build) {
    if (!myGroups.containsKey(groupId)) {
      return false;
    }

    int parsedVersion = tryToParse(version);
    if (parsedVersion < 0) {
      return false;
    }
    EventGroupFilterRules condition = myGroups.get(groupId);
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
    EventGroupsFilterRules rules = (EventGroupsFilterRules)o;
    return Objects.equals(myGroups, rules.myGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroups);
  }
}
