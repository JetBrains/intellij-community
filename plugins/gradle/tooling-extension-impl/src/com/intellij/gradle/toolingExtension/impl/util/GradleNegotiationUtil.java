// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.util.ReflectionUtil;

import java.io.File;

public final class GradleNegotiationUtil {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().getBaseVersion();
  private static final boolean is33OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("3.3")) >= 0;
  private static final boolean is37OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("3.7")) >= 0;
  private static final boolean is45OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("4.5")) >= 0;
  private static final boolean is51OrBetter = gradleBaseVersion.compareTo(GradleVersion.version("5.1")) >= 0;

  /**
   * Right now, there is no public API available to get this identityPath
   * Agreement with Gradle: We can use ProjectInternal for now.
   * This identity path will get a public tooling API which will replace the cast.
   * Until then, this API will be kept stable as agreement between Gradle and JetBrains
   * <p>
   * Note: a project identity path was introduced with Gradle 3.3 in commit
   * <a href="https://github.com/gradle/gradle/commit/2c009b27b97c1564344f3cc93258ce5a0e18a03f">2c009b27b97c1564344f3cc93258ce5a0e18a03f</a>
   */
  public static @Nullable String getProjectIdentityPath(@NotNull Project project) {
    if (is37OrBetter) {
      return ((ProjectInternal)project).getIdentityPath().getPath();
    }
    return null;
  }

  public static boolean isConfigurationCanBeResolved(@NotNull Configuration configuration, boolean defaultResult) {
    if (is33OrBetter) {
      return configuration.isCanBeResolved();
    }
    return defaultResult;
  }

  public static @NotNull String getProjectName(@NotNull ProjectComponentIdentifier identifier) {
    if (is45OrBetter) {
      return identifier.getProjectName();
    }
    return identifier.getProjectPath();
  }

  public static @Nullable File getTaskArchiveFile(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return ReflectionUtil.reflectiveGetProperty(task, "getArchiveFile", RegularFile.class).getAsFile();
    }
    return ReflectionUtil.reflectiveCall(task, "getArchivePath", File.class);
  }

  public static @Nullable String getTaskArchiveFileName(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return ReflectionUtil.reflectiveGetProperty(task, "getArchiveFileName", String.class);
    }
    return ReflectionUtil.reflectiveCall(task, "getArchiveName", String.class);
  }
}
