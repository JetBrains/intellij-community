// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;

/**
 * @author Vladislav.Soroka
 */
final class GradleJvmStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull final Project project) {
    configureBuildClasspath(project);
  }

  private static void configureBuildClasspath(@NotNull final Project project) {
    GradleBuildClasspathManager.getInstance(project).reload();
  }
}
