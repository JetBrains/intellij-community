// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.util.ValidatorStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;

public class EventGroupFilterRules<T extends Comparable<T>> {
  private final List<BuildRange<T>> builds;
  private final List<VersionRange> versions;

  public EventGroupFilterRules(@NotNull List<BuildRange<T>> builds, @NotNull List<VersionRange> versions) {
    this.builds = builds;
    this.versions = versions;
  }

  @NotNull
  public static <P extends Comparable<P>> EventGroupFilterRules<P> create(@NotNull EventGroupRemoteDescriptors.EventGroupRemoteDescriptor group, @NotNull EventLogBuildProducer<P> buildProducer) {
    return create(group.builds, group.versions, buildProducer);
  }

  @NotNull
  private static <P extends Comparable<P>> EventGroupFilterRules<P> create(@Nullable List<EventGroupRemoteDescriptors.GroupBuildRange> builds, @Nullable List<EventGroupRemoteDescriptors.GroupVersionRange> versions, @NotNull EventLogBuildProducer<P> buildProducer) {
    final List<BuildRange<P>> buildRanges = builds != null && !builds.isEmpty() ? toBuildRanges(builds, buildProducer) : emptyList();
    final List<EventGroupFilterRules.VersionRange> versionRanges = versions != null && !versions.isEmpty() ? toVersionRanges(versions) : emptyList();
    return new EventGroupFilterRules<>(buildRanges, versionRanges);
  }

  @NotNull
  private static <P extends Comparable<P>> List<BuildRange<P>> toBuildRanges(@NotNull List<EventGroupRemoteDescriptors.GroupBuildRange> builds, @NotNull EventLogBuildProducer<P> buildProducer) {
    List<BuildRange<P>> result = new ArrayList<>();
    for (EventGroupRemoteDescriptors.GroupBuildRange build : builds) {
      result.add(EventGroupFilterRules.BuildRange.create(build.from, build.to, buildProducer));
    }
    return result;
  }

  @NotNull
  private static List<VersionRange> toVersionRanges(@NotNull List<EventGroupRemoteDescriptors.GroupVersionRange> versions) {
    List<EventGroupFilterRules.VersionRange> result = new ArrayList<>();
    for (EventGroupRemoteDescriptors.GroupVersionRange version : versions) {
      result.add(EventGroupFilterRules.VersionRange.create(version.from, version.to));
    }
    return result;
  }

  public boolean accepts(@Nullable T build, int version) {
    return accepts(build) && acceptsVersion(version);
  }

  public boolean accepts(@Nullable T build) {
    if (!isValid()) {
      return false;
    }
    return acceptsBuild(build);
  }

  private boolean acceptsBuild(@Nullable T build) {
    if (builds.isEmpty()) return true;

    return build != null && builds.stream().anyMatch(b -> b.contains(build));
  }

  private boolean acceptsVersion(int version) {
    if (versions.isEmpty()) return true;
    return version > 0 && versions.stream().anyMatch(v -> v.contains(version));
  }


  private boolean isValid() {
    return !builds.isEmpty() || !versions.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventGroupFilterRules<?> condition = (EventGroupFilterRules<?>)o;
    return Objects.equals(builds, condition.builds) &&
           Objects.equals(versions, condition.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(builds, versions);
  }

  public static class BuildRange<T extends Comparable<T>> {
    private final T myFrom;
    private final T myTo;

    public BuildRange(@Nullable T from, @Nullable T to) {
      myFrom = from;
      myTo = to;
    }

    @NotNull
    public static <P extends Comparable<P>> BuildRange<P> create(@Nullable String from, @Nullable String to, EventLogBuildProducer<? extends P> buildProducer) {
      return new BuildRange<>(
        !ValidatorStringUtil.isEmpty(from) ? buildProducer.create(from) : null,
        !ValidatorStringUtil.isEmpty(to) ? buildProducer.create(to) : null
      );
    }

    public boolean contains(@NotNull T build) {
      if (myTo == null && myFrom == null) return false;

      return (myTo == null || myTo.compareTo(build) > 0) &&
             (myFrom == null || myFrom.compareTo(build) <= 0);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BuildRange<?> range = (BuildRange<?>)o;
      return Objects.equals(myFrom, range.myFrom) &&
             Objects.equals(myTo, range.myTo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFrom, myTo);
    }
  }

  public static class VersionRange {
    private final int myFrom;
    private final int myTo;

    public VersionRange(int from, int to) {
      myFrom = from;
      myTo = to;
    }

    @NotNull
    public static VersionRange create(@Nullable String from, @Nullable String to) {
      return new VersionRange(
        from == null ? 0 : tryToParse(from, Integer.MAX_VALUE),
        to == null ? Integer.MAX_VALUE : tryToParse(to, 0)
      );
    }

    private static int tryToParse(@Nullable String value, int defaultValue) {
      try {
        if (value != null) {
          return Integer.parseInt(value.trim());
        }
      }
      catch (NumberFormatException e) {
        // ignore
      }
      return defaultValue;
    }

    public boolean contains(int current) {
      return current >= myFrom && current < myTo;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VersionRange range = (VersionRange)o;
      return myFrom == range.myFrom &&
             myTo == range.myTo;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFrom, myTo);
    }
  }
}
