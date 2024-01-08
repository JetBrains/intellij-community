// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class GradleNegotiationUtil {

  private static final boolean is33OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("3.3");
  private static final boolean is37OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("3.7");
  private static final boolean is45OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.5");
  private static final boolean is49OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.9");
  private static final boolean is51OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("5.1");

  /**
   * Right now, there is no public API available to get this identityPath
   * Agreement with Gradle: We can use ProjectInternal for now.
   * This identity path will get a public tooling API which will replace the cast.
   * Until then, this API will be kept stable as an agreement between Gradle and JetBrains
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

  public static @NotNull String getProjectDisplayName(@NotNull Project project) {
    if (is33OrBetter) {
      return project.getDisplayName();
    }
    if (project.getParent() == null && project.getGradle().getParent() == null) {
      return "root project '" + project.getName() + "'";
    }
    return "project '" + project.getPath() + "'";
  }

  public static @Nullable Class<?> getTaskIdentityType(@NotNull TaskInternal task) {
    if (is49OrBetter) {
      return task.getTaskIdentity().type;
    }
    if (task instanceof DynamicObjectAware) {
      DynamicObject dynamicObject = ((DynamicObjectAware)task).getAsDynamicObject();
      if (dynamicObject instanceof AbstractDynamicObject) {
        return ((AbstractDynamicObject)dynamicObject).getPublicType();
      }
    }
    return null;
  }

  public static @Nullable File getTaskArchiveFile(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return GradleReflectionUtil.reflectiveGetProperty(task, "getArchiveFile", RegularFile.class).getAsFile();
    }
    return GradleReflectionUtil.reflectiveCall(task, "getArchivePath", File.class);
  }

  public static @Nullable String getTaskArchiveFileName(@NotNull AbstractArchiveTask task) {
    if (is51OrBetter) {
      return GradleReflectionUtil.reflectiveGetProperty(task, "getArchiveFileName", String.class);
    }
    return GradleReflectionUtil.reflectiveCall(task, "getArchiveName", String.class);
  }
}
