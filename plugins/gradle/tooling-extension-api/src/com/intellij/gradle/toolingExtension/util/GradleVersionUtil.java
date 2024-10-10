// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util;

import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class GradleVersionUtil {

  private static final @NotNull GradleVersion currentGradleVersion = GradleVersion.current().getBaseVersion();

  public static boolean isCurrentGradleAtLeast(@NotNull String version) {
    return currentGradleVersion.compareTo(GradleVersion.version(version)) >= 0;
  }

  public static boolean isGradleAtLeast(@NotNull GradleVersion actualVersion, @NotNull String version) {
    return actualVersion.getBaseVersion().compareTo(GradleVersion.version(version)) >= 0;
  }

  public static boolean isGradleAtLeast(@NotNull String actualVersion, @NotNull String version) {
    return isGradleAtLeast(GradleVersion.version(actualVersion), version);
  }

  public static boolean isCurrentGradleOlderThan(@NotNull String version) {
    return !isCurrentGradleAtLeast(version);
  }

  public static boolean isGradleOlderThan(@NotNull GradleVersion actualVersion, @NotNull String version) {
    return !isGradleAtLeast(actualVersion, version);
  }

  public static boolean isGradleOlderThan(@NotNull String actualVersion, @NotNull String version) {
    return !isGradleAtLeast(actualVersion, version);
  }

  /**
   * @see GradleVersionUtil#isGradleAtLeast
   * @see GradleVersionUtil#isCurrentGradleAtLeast
   * @deprecated Gradle version comparisons '>' and '<=' aren't logical.
   * Changes can be made only in the specific version and present in the future.
   * We always can identify the version where new changes were made.
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isCurrentGradleNewerThan(@NotNull String version) {
    return currentGradleVersion.compareTo(GradleVersion.version(version)) > 0;
  }

  /**
   * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isGradleNewerThan(@NotNull GradleVersion actualVersion, @NotNull String version) {
    return actualVersion.getBaseVersion().compareTo(GradleVersion.version(version)) > 0;
  }

  /**
   * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isGradleNewerThan(@NotNull String actualVersion, @NotNull String version) {
    return isGradleNewerThan(GradleVersion.version(actualVersion), version);
  }

  /**
   * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isCurrentGradleOlderOrSameAs(@NotNull String version) {
    return !isCurrentGradleNewerThan(version);
  }

  /**
   * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isGradleOlderOrSameAs(@NotNull GradleVersion actualVersion, @NotNull String version) {
    return !isGradleNewerThan(actualVersion, version);
  }

  /**
   * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static boolean isGradleOlderOrSameAs(@NotNull String actualVersion, @NotNull String version) {
    return !isGradleNewerThan(actualVersion, version);
  }
}
