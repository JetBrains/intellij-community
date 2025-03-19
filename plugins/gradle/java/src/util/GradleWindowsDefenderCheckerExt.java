// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.diagnostic.WindowsDefenderChecker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public final class GradleWindowsDefenderCheckerExt implements WindowsDefenderChecker.Extension {
  @Override
  public @NotNull Collection<Path> getPaths(@Nullable Project project,@Nullable Path projectPath) {
    if (project != null && !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()) {
      return getGradlePaths();
    } else if (projectPath != null){
      VirtualFile projectDir = VirtualFileManager.getInstance().findFileByNioPath(projectPath);
      VirtualFile[] children = projectDir != null ? projectDir.getChildren() : VirtualFile.EMPTY_ARRAY;
      boolean isGradleProject = ContainerUtil.exists(children, file -> GradleConstants.KNOWN_GRADLE_FILES.contains(file.getName()));
      if (isGradleProject) {
        return getGradlePaths();
      }
    }

    return List.of();
  }

  private static Collection<Path> getGradlePaths() {
    var envVar = System.getenv("GRADLE_USER_HOME");
    var gradleDir = envVar != null ? Path.of(envVar) : Path.of(System.getProperty("user.home"), ".gradle");
    if (Files.isDirectory(gradleDir)) {
      return List.of(gradleDir);
    }
    return List.of();
  }
}
