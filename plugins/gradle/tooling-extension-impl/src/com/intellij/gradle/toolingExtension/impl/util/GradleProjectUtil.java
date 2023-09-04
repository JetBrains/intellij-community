// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleProjectUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is37OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("3.7")) >= 0;

  /**
   * Right now, there is no public API available to get this identityPath
   * Agreement with Gradle: We can use ProjectInternal for now.
   * This identity path will get a public tooling API which will replace the cast.
   * Until then, this API will be kept stable as agreement between Gradle and JetBrains
   * <p>
   * Note: a project identity path was introduced with Gradle 3.3 in commit
   * <a href="https://github.com/gradle/gradle/commit/2c009b27b97c1564344f3cc93258ce5a0e18a03f">2c009b27b97c1564344f3cc93258ce5a0e18a03f</a>
   */
  public static @Nullable String getIdentityPath(@NotNull Project project) {
    if (is37OrBetter) {
      return ((ProjectInternal)project).getIdentityPath().getPath();
    }
    return null;
  }
}
