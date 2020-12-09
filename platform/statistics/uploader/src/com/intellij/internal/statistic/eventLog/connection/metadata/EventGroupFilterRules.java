// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.intellij.internal.statistic.eventLog.EventLogBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.internal.statistic.StatisticsStringUtil.isNotEmpty;
import static java.util.Collections.emptyList;

public class EventGroupFilterRules {
  private final List<BuildRange> builds;
  private final List<VersionRange> versions;

  public EventGroupFilterRules(@NotNull List<BuildRange> builds, @NotNull List<VersionRange> versions) {
    this.builds = builds;
    this.versions = versions;
  }

  @NotNull
  public static EventGroupFilterRules create(@NotNull EventGroupRemoteDescriptors.EventGroupRemoteDescriptor group) {
    return create(group.builds, group.versions);
  }

  @NotNull
  private static EventGroupFilterRules create(@Nullable List<EventGroupRemoteDescriptors.GroupBuildRange> builds, @Nullable List<EventGroupRemoteDescriptors.GroupVersionRange> versions) {
    final List<BuildRange> buildRanges = builds != null && !builds.isEmpty() ? toBuildRanges(builds) : emptyList();
    final List<EventGroupFilterRules.VersionRange> versionRanges = versions != null && !versions.isEmpty() ? toVersionRanges(versions) : emptyList();
    return new EventGroupFilterRules(buildRanges, versionRanges);
  }

  @NotNull
  private static List<BuildRange> toBuildRanges(@NotNull List<EventGroupRemoteDescriptors.GroupBuildRange> builds) {
    List<BuildRange> result = new ArrayList<>();
    for (EventGroupRemoteDescriptors.GroupBuildRange build : builds) {
      result.add(EventGroupFilterRules.BuildRange.create(build.from, build.to));
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

  public boolean accepts(@Nullable EventLogBuild build, int version) {
    return accepts(build) && acceptsVersion(version);
  }

  public boolean accepts(@Nullable EventLogBuild build) {
    if (!isValid()) {
      return false;
    }
    return acceptsBuild(build);
  }

  private boolean acceptsBuild(@Nullable EventLogBuild build) {
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
    EventGroupFilterRules condition = (EventGroupFilterRules)o;
    return Objects.equals(builds, condition.builds) &&
           Objects.equals(versions, condition.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(builds, versions);
  }

  public static class BuildRange {
    private final EventLogBuild myFrom;
    private final EventLogBuild myTo;

    public BuildRange(@Nullable EventLogBuild from, @Nullable EventLogBuild to) {
      myFrom = from;
      myTo = to;
    }

    @NotNull
    public static BuildRange create(@Nullable String from, @Nullable String to) {
      return new BuildRange(
        isNotEmpty(from) ? EventLogBuild.fromString(from) : null,
        isNotEmpty(to) ? EventLogBuild.fromString(to) : null
      );
    }

    public boolean contains(@NotNull EventLogBuild build) {
      if (myTo == null && myFrom == null) return false;

      return (myTo == null || myTo.compareTo(build) > 0) &&
             (myFrom == null || myFrom.compareTo(build) <= 0);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BuildRange range = (BuildRange)o;
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
