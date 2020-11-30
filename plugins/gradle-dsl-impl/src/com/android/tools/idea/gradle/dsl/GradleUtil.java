// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl;

import static com.google.common.base.Splitter.on;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.google.common.base.Strings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.io.File;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

public final class GradleUtil {

  public static final String GRADLE_PATH_SEPARATOR = ":";
  public static final String FN_GRADLE_PROPERTIES = "gradle.properties";

  @Nullable
  public static VirtualFile getGradleSettingsFile(@NotNull File dirPath) {
    File gradleSettingsFilePath = GradleDslBuildScriptUtil.findGradleSettingsFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleSettingsFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = GradleDslBuildScriptUtil.findGradleBuildFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleBuildFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  @NotNull
  public static File getBaseDirPath(@NotNull Project project) {
    if (project.isDefault()) {
      return new File("");
    }
    return new File(Objects.requireNonNull(FileUtil.toCanonicalPath(project.getBasePath())));
  }

  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    File moduleRoot = findModuleRootFolderPath(module);
    return moduleRoot != null ? getGradleBuildFile(moduleRoot) : null;
  }

  @Nullable
  private static File findModuleRootFolderPath(@NotNull Module module) {
    @SystemIndependent String path = getModuleDirPath(module);
    if (path == null) return null;
    return new File(PathUtil.toSystemDependentName(path));
  }

  @Nullable
  @SystemIndependent
  public static String getModuleDirPath(@NotNull Module module) {
    String linkedProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (!Strings.isNullOrEmpty(linkedProjectPath)) {
      return linkedProjectPath;
    }
    @SystemIndependent String moduleFilePath = module.getModuleFilePath();
    return VfsUtil.getParentDir(moduleFilePath);
  }

  @NotNull
  public static List<String> getPathSegments(@NotNull String gradlePath) {
    return on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(gradlePath);
  }

}
