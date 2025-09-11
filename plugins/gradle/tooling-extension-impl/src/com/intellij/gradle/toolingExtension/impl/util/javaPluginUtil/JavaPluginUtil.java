// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil;

import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaPluginUtil {

  public static @Nullable JavaPluginExtension getJavaPluginExtension(@NotNull Project project) {
    if (GradleVersionSpecificsUtil.isPluginExtensionSupported(GradleVersion.current())) {
      return project.getExtensions().findByType(JavaPluginExtension.class);
    }
    return null;
  }

  public static @Nullable JavaToolchainSpec getToolchain(@NotNull Project project) {
    if (GradleVersionSpecificsUtil.isJavaToolchainSupported(GradleVersion.current())) {
      JavaPluginExtension javaExtension = getJavaPluginExtension(project);
      if (javaExtension != null) {
        return javaExtension.getToolchain();
      }
    }
    return null;
  }

  private static @NotNull JavaPluginAccessor getJavaPluginAccessor(@NotNull Project p) {
    if (GradleVersionUtil.isCurrentGradleAtLeast("8.2")) {
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
