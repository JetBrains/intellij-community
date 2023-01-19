// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.diagnostic.WindowsDefenderChecker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class GradleWindowsDefenderCheckerExt implements WindowsDefenderChecker.Extension {
  @Override
  public @NotNull Collection<Path> getPaths(@NotNull Project project) {
    if (!GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()) {
      var envVar = System.getenv("GRADLE_USER_HOME");
      var gradleDir = envVar != null ? Path.of(envVar) : Path.of(System.getProperty("user.home"), ".gradle");
      if (Files.isDirectory(gradleDir)) {
        return List.of(gradleDir);
      }
    }

    return List.of();
  }
}
