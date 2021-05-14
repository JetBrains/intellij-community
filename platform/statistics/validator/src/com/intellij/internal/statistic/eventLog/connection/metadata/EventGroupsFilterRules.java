// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EventGroupsFilterRules<T extends Comparable<T>> {
  private final Map<String, EventGroupFilterRules<T>> myGroups;
  private final EventLogBuildParser<T> myBuildProducer;

  private EventGroupsFilterRules(@NotNull Map<String, EventGroupFilterRules<T>> groups,
                                 @NotNull EventLogBuildParser<T> producer) {
    myGroups = groups;
    myBuildProducer = producer;
  }

  @NotNull
  public static <P extends Comparable<P>> EventGroupsFilterRules<P> create(@NotNull Map<String, EventGroupFilterRules<P>> groups,
                                                                           @NotNull EventLogBuildParser<P> buildProducer) {
    return new EventGroupsFilterRules<>(groups, buildProducer);
  }

  @NotNull
  public static EventGroupsFilterRules<EventLogBuild> empty() {
    return new EventGroupsFilterRules<>(Collections.emptyMap(), EventLogBuild.EVENT_LOG_BUILD_PRODUCER);
  }

  public boolean accepts(@NotNull String groupId, @Nullable String version, @NotNull String build) {
    if (!myGroups.containsKey(groupId)) {
      return false;
    }

    int parsedVersion = tryToParse(version);
    if (parsedVersion < 0) {
      return false;
    }
    EventGroupFilterRules<T> condition = myGroups.get(groupId);
    return condition.accepts(myBuildProducer.parse(build), parsedVersion);
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
    EventGroupsFilterRules<?> rules = (EventGroupsFilterRules<?>)o;
    return Objects.equals(myGroups, rules.myGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroups);
  }

  @NotNull
  public static EventGroupsFilterRules<EventLogBuild> create(@Nullable String metadataContent) throws EventLogMetadataParseException {
    EventGroupRemoteDescriptors groups = EventGroupRemoteDescriptors.create(metadataContent);
    return create(groups, EventLogBuild.EVENT_LOG_BUILD_PRODUCER);
  }

  @NotNull
  public static <P extends Comparable<P>> EventGroupsFilterRules<P> create(@NotNull EventGroupRemoteDescriptors groups,
                                                                           @NotNull EventLogBuildParser<P> buildProducer) {
    Map<String, EventGroupFilterRules<P>> groupToCondition = new HashMap<>();
    for (EventGroupRemoteDescriptors.EventGroupRemoteDescriptor group : groups.groups) {
      groupToCondition.put(group.id, EventGroupFilterRules.create(group, buildProducer));
    }
    return create(groupToCondition, buildProducer);
  }
}
