// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.applicationPluginUtil;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ApplicationPluginUtil {

  private static final boolean is41OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("4.10");

  private static @NotNull ApplicationPluginAccessor getApplicationPluginAccessor(@NotNull Project gradleProject) {
    if (is41OrBetter) {
      return new ExtensionApplicationPluginAccessor(gradleProject);
    }
    else {
      return new ConventionApplicationPluginAccessor(gradleProject);
    }
  }

  public static @Nullable String getMainClass(@NotNull Project gradleProject) {
    return getApplicationPluginAccessor(gradleProject).getMainClass();
  }
}
