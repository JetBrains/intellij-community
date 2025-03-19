// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class GradleLifecycleUtil {

  public static void beforeProject(@NotNull Gradle gradle, @NotNull Consumer<Project> action) {
    if (GradleVersionUtil.isCurrentGradleAtLeast("8.8")) {
      gradle.getLifecycle().beforeProject(it -> action.accept(it));
      return;
    }
    gradle.allprojects(it -> action.accept(it));
  }

  public static void afterProject(@NotNull Gradle gradle, @NotNull Consumer<Project> action) {
    if (GradleVersionUtil.isCurrentGradleAtLeast("8.8")) {
      gradle.getLifecycle().afterProject(it -> action.accept(it));
      return;
    }
    gradle.allprojects(it -> it.afterEvaluate(project -> action.accept(it)));
  }
}