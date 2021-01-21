// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.config.bean;

import com.intellij.internal.statistic.eventLog.EventLogMajorVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventLogMajorVersionBorders {
  @Nullable
  public String from;
  @Nullable
  public String to;

  public boolean accept(@NotNull String current) {
    EventLogMajorVersion build = EventLogMajorVersion.fromString(current);
    if (!isValidMajorVersion(build)) {
      return false;
    }

    EventLogMajorVersion toVersion = EventLogMajorVersion.fromString(to);
    EventLogMajorVersion fromVersion = EventLogMajorVersion.fromString(from);
    if (!isValidMajorVersion(toVersion) && !isValidMajorVersion(fromVersion)) {
      return false;
    }

    return (fromVersion == null || fromVersion.compareTo(build) <= 0) &&
           (toVersion == null || toVersion.compareTo(build) > 0);
  }

  private static boolean isValidMajorVersion(@Nullable EventLogMajorVersion build) {
    if (build == null) return false;

    int[] components = build.getComponents();
    return components.length > 0 && components[0] > 0;
  }
}
