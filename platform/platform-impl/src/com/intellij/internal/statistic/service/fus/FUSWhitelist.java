// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FUSWhitelist {
  private Map<String, List<VersionRange>> myGroups;

  public FUSWhitelist() {
  }

  private FUSWhitelist(@NotNull Map<String, List<VersionRange>> groups) {
    myGroups = groups;
  }

  @NotNull
  public static FUSWhitelist create(@NotNull Map<String, List<VersionRange>> groupsToVersion) {
    return new FUSWhitelist(groupsToVersion);
  }

  @NotNull
  public static FUSWhitelist empty() {
    return new FUSWhitelist(Collections.emptyMap());
  }

  @XMap(propertyElementName = "groups", keyAttributeName = "id", entryTagName = "group")
  public Map<String, List<VersionRange>> getGroups() {
    return myGroups;
  }

  public void setGroups(Map<String, List<VersionRange>> groups) {
    myGroups = groups;
  }

  public boolean accepts(@NotNull String groupId, @Nullable String version) {
    final int parsed = tryToParse(version, -1);
    if (parsed < 0) {
      return false;
    }
    return accepts(groupId, parsed);
  }

  public boolean accepts(@NotNull String groupId, int version) {
    if (!myGroups.containsKey(groupId)) {
      return false;
    }
    final List<VersionRange> ranges = myGroups.get(groupId);
    return ranges.isEmpty() || ContainerUtil.find(ranges, range -> range.contains(version)) != null;
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

  @Tag("version")
  public static class VersionRange {
    private int myFrom;
    private int myTo;

    public VersionRange() {
    }

    @NotNull
    public static VersionRange create(@Nullable String from, @Nullable String to) {
      final VersionRange range = new VersionRange();
      range.setFrom(from == null ? 0 : tryToParse(from, Integer.MAX_VALUE));
      range.setTo(to == null ? Integer.MAX_VALUE : tryToParse(to, 0));
      return range;
    }

    @Attribute("from")
    public int getFrom() {
      return myFrom;
    }

    public void setFrom(int from) {
      myFrom = from;
    }

    @Attribute("to")
    public int getTo() {
      return myTo;
    }

    public void setTo(int to) {
      myTo = to;
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
