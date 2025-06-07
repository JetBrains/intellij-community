// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class GradleVersionComparator implements Comparable<GradleVersion> {
  private final GradleVersion myVersion;
  private final Map<String, Integer> myResults = new HashMap<>();
  private final Map<String, GradleVersion> myVersionsMap = new HashMap<>();

  public GradleVersionComparator(@NotNull GradleVersion gradleVersion) {
    myVersion = gradleVersion;
  }

  @Override
  public int compareTo(@NotNull GradleVersion gradleVersion) {
    if (myVersion == gradleVersion) return 0;
    String version = gradleVersion.getVersion();
    if (myVersion.getVersion().equals(version)) return 0;
    Integer cached = myResults.get(version);
    if (cached != null) return cached;

    int result = myVersion.compareTo(gradleVersion);
    myResults.put(version, result);
    return result;
  }

  public boolean lessThan(@NotNull GradleVersion gradleVersion) {
    return compareTo(gradleVersion) < 0;
  }

  public boolean lessThan(@NotNull String gradleVersion) {
    return lessThan(getGradleVersion(gradleVersion));
  }

  public boolean isOrGreaterThan(@NotNull GradleVersion gradleVersion) {
    return compareTo(gradleVersion) >= 0;
  }

  public boolean isOrGreaterThan(@NotNull String gradleVersion) {
    return isOrGreaterThan(getGradleVersion(gradleVersion));
  }

  public boolean is(@NotNull GradleVersion gradleVersion) {
    return compareTo(gradleVersion) == 0;
  }

  public boolean is(@NotNull String gradleVersion) {
    return is(getGradleVersion(gradleVersion));
  }

  private @NotNull GradleVersion getGradleVersion(@NotNull String gradleVersion) {
    GradleVersion version = myVersionsMap.get(gradleVersion);
    if (version == null) {
      version = GradleVersion.version(gradleVersion);
      myVersionsMap.put(gradleVersion, version);
    }
    return version;
  }
}
