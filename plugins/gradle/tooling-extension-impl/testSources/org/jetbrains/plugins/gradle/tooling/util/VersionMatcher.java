// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;

/**
 * @author Vladislav.Soroka
 */
public final class VersionMatcher {
  private static final String RANGE_TOKEN = " <=> ";

  @NotNull
  private final GradleVersion myGradleVersion;

  public VersionMatcher(@NotNull GradleVersion version) {
    myGradleVersion = version;
  }

  public boolean isVersionMatch(@Nullable TargetVersions targetVersions) {
    if (targetVersions == null) return true;
    return isVersionMatch(targetVersions.value(), targetVersions.checkBaseVersions());
  }

  public boolean isVersionMatch(@Nullable String[] targetVersions, final boolean checkBaseVersions) {
    for (String t : targetVersions) {
      if (!isVersionMatch(t, checkBaseVersions)) return false;
    }
    return true;
  }

  public boolean isVersionMatch(@Nullable String targetVersions, boolean checkBaseVersions) {
    if (targetVersions == null || targetVersions.isEmpty()) return true;

    final GradleVersion current = adjust(myGradleVersion, checkBaseVersions);

    if (targetVersions.endsWith("+")) {
      String minVersion = targetVersions.substring(0, targetVersions.length() - 1);
      return compare(current, minVersion, checkBaseVersions) >= 0;
    }
    else if (targetVersions.startsWith("!")) {
      String version = targetVersions.substring(1);
      return compare(current, version, checkBaseVersions) != 0;
    }
    else if (targetVersions.startsWith("<")) {
      if (targetVersions.startsWith("<=")) {
        String maxVersion = targetVersions.substring(2);
        return compare(current, maxVersion, checkBaseVersions) <= 0;
      }
      else {
        String maxVersion = targetVersions.substring(1);
        return compare(current, maxVersion, checkBaseVersions) < 0;
      }
    }
    else {
      final int rangeIndex = targetVersions.indexOf(RANGE_TOKEN);
      if (rangeIndex != -1) {
        String minVersion = targetVersions.substring(0, rangeIndex);
        String maxVersion = targetVersions.substring(rangeIndex + RANGE_TOKEN.length());
        return compare(current, minVersion, checkBaseVersions) >= 0 &&
          compare(current, maxVersion, checkBaseVersions) <= 0;
      }
      else {
        return compare(current, targetVersions, checkBaseVersions) == 0;
      }
    }
  }


  private static int compare(@NotNull GradleVersion gradleVersion, @NotNull String otherGradleVersion, boolean checkBaseVersions) {
    return gradleVersion.compareTo(adjust(GradleVersion.version(otherGradleVersion), checkBaseVersions));
  }

  private static GradleVersion adjust(@NotNull GradleVersion version, boolean checkBaseVersions) {
    return checkBaseVersions ? version.getBaseVersion() : version;
  }
}
