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
    if (targetVersions == null || targetVersions.value().isEmpty()) return true;

    final GradleVersion current = adjust(myGradleVersion, targetVersions.checkBaseVersions());

    if (targetVersions.value().endsWith("+")) {
      String minVersion = targetVersions.value().substring(0, targetVersions.value().length() - 1);
      return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0;
    }
    else if (targetVersions.value().startsWith("<")) {
      if (targetVersions.value().startsWith("<=")) {
        String maxVersion = targetVersions.value().substring(2);
        return compare(current, maxVersion, targetVersions.checkBaseVersions()) <= 0;
      }
      else {
        String maxVersion = targetVersions.value().substring(1);
        return compare(current, maxVersion, targetVersions.checkBaseVersions()) < 0;
      }
    }
    else {
      final int rangeIndex = targetVersions.value().indexOf(RANGE_TOKEN);
      if (rangeIndex != -1) {
        String minVersion = targetVersions.value().substring(0, rangeIndex);
        String maxVersion = targetVersions.value().substring(rangeIndex + RANGE_TOKEN.length());
        return compare(current, minVersion, targetVersions.checkBaseVersions()) >= 0 &&
               compare(current, maxVersion, targetVersions.checkBaseVersions()) <= 0;
      }
      else {
        return compare(current, targetVersions.value(), targetVersions.checkBaseVersions()) == 0;
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
