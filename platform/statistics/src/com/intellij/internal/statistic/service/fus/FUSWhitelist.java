// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FUSWhitelist {
  private Map<String, GroupFilterCondition> myGroups;

  public FUSWhitelist() {
  }

  private FUSWhitelist(@NotNull Map<String, GroupFilterCondition> groups) {
    myGroups = groups;
  }

  @NotNull
  public static FUSWhitelist create(@NotNull Map<String, GroupFilterCondition> groups) {
    return new FUSWhitelist(groups);
  }

  @NotNull
  public static FUSWhitelist empty() {
    return new FUSWhitelist(Collections.emptyMap());
  }

  public boolean accepts(@NotNull String groupId, @Nullable String version, @NotNull String build) {
    if (!myGroups.containsKey(groupId)) {
      return false;
    }

    final int parsedVersion = tryToParse(version, -1);
    if (parsedVersion < 0) {
      return false;
    }
    final GroupFilterCondition condition = myGroups.get(groupId);
    return condition.accepts(build, parsedVersion);
  }

  public int getSize() {
    return myGroups.size();
  }

  public boolean isEmpty() {
    return myGroups.isEmpty();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FUSWhitelist whitelist = (FUSWhitelist)o;
    return Objects.equals(myGroups, whitelist.myGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroups);
  }

  public static class GroupFilterCondition {
    private final List<BuildRange> builds;
    private final List<VersionRange> versions;

    public GroupFilterCondition(@NotNull List<BuildRange> builds, @NotNull List<VersionRange> versions) {
      this.builds = builds;
      this.versions = versions;
    }

    public boolean accepts(@NotNull String build, int version) {
      if (!isValid()) {
        return false;
      }
      return acceptsBuild(build) && acceptsVersion(version);
    }

    private boolean acceptsBuild(@NotNull String build) {
      if (builds.isEmpty()) return true;

      final BuildNumber number = BuildNumber.fromString(build);
      return number != null && builds.stream().anyMatch(b -> b.contains(number));
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
      GroupFilterCondition condition = (GroupFilterCondition)o;
      return Objects.equals(builds, condition.builds) &&
             Objects.equals(versions, condition.versions);
    }

    @Override
    public int hashCode() {
      return Objects.hash(builds, versions);
    }
  }

  public static class BuildRange {
    private final BuildNumber myFrom;
    private final BuildNumber myTo;

    public BuildRange(@Nullable BuildNumber from, @Nullable BuildNumber to) {
      myFrom = from;
      myTo = to;
    }

    @NotNull
    public static BuildRange create(@Nullable String from, @Nullable String to) {
      return new BuildRange(
        StringUtil.isNotEmpty(from) ? BuildNumber.fromString(from) : null,
        StringUtil.isNotEmpty(to) ? BuildNumber.fromString(to) : null
      );
    }

    public boolean contains(@NotNull BuildNumber build) {
      return (myTo == null || myTo.compareTo(build) > 0) && (myFrom == null || myFrom.compareTo(build) <= 0);
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
