/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface GradleSettingsModel extends GradleFileModel {
  /**
   * @deprecated Use {@link ProjectBuildModel#getProjectSettingsModel()} instead.
   */
  @Deprecated
  @Nullable
  static GradleSettingsModel get(@NotNull Project project) {
    return GradleModelProvider.get().getSettingsModel(project);
  }

  /**
   * Obtains ONLY the model for the Gradle settings.gradle file. DO NOT use this unless you are sure you need to.
   * Use {@link ProjectBuildModel#getProjectSettingsModel()} instead. You should not attempt to obtain
   * {@link GradleBuildModel}s from the returns settings model.
   */
  @NotNull
  static GradleSettingsModel get(@NotNull VirtualFile settingFile, @NotNull Project hostProject) {
    return GradleModelProvider.get().getSettingsModel(settingFile, hostProject);
  }

  @NotNull
  List<String> modulePaths();

  void addModulePath(@NotNull String modulePath);

  void removeModulePath(@NotNull String modulePath);

  void replaceModulePath(@NotNull String oldModulePath, @NotNull String newModulePath);

  @Nullable
  File moduleDirectory(String modulePath);

  void setModuleDirectory(@NotNull String modulePath, @NotNull File moduleDir);

  @Nullable
  String moduleWithDirectory(@NotNull File moduleDir);

  @Nullable
  GradleBuildModel moduleModel(@NotNull String modulePath);

  @Nullable
  String parentModule(@NotNull String modulePath);

  @Nullable
  GradleBuildModel getParentModuleModel(@NotNull String modulePath);

  @Nullable
  File buildFile(@NotNull String modulePath);

  /**
   * If models are available you might want to use {@link org.jetbrains.plugins.gradle.settings.GradleProjectSettings#getCompositeBuild()} instead.
   *
   * @return files representing the root folders of the included builds
   */
  @NotNull
  List<VirtualFile> includedBuilds();
}
