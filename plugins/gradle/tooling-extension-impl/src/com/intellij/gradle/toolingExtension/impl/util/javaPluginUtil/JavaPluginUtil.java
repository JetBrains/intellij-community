// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaPluginUtil {

  private static final boolean is82OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.2");

  private static @NotNull JavaPluginAccessor getJavaPluginAccessor(@NotNull Project p) {
    if (is82OrBetter) {
      return new ExtensionJavaPluginAccessor(p);
    }
    else {
      return new ConventionJavaPluginAccessor(p);
    }
  }

  public static @Nullable String getSourceCompatibility(Project project) {
    return getJavaPluginAccessor(project).getSourceCompatibility();
  }

  public static @Nullable String getTargetCompatibility(Project project) {
    return getJavaPluginAccessor(project).getTargetCompatibility();
  }

  public static @Nullable SourceSetContainer getSourceSetContainer(Project project) {
    return getJavaPluginAccessor(project).getSourceSetContainer();
  }

  public static boolean isJavaPluginApplied(Project project) {
    return getJavaPluginAccessor(project).isJavaPluginApplied();
  }
}
