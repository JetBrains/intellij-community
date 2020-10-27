/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProjectBuildModelImpl implements ProjectBuildModel {
  @NotNull private final BuildModelContext myBuildModelContext;
  @Nullable private final GradleBuildFile myProjectBuildFile;

  /**
   * @param project the project this model should be built for
   * @param file the file contain the projects main build.gradle
   * @param buildModelContext
   */
  public ProjectBuildModelImpl(@NotNull Project project, @Nullable VirtualFile file, @NotNull BuildModelContext buildModelContext) {
    myBuildModelContext = buildModelContext;
    myProjectBuildFile = myBuildModelContext.parseProjectBuildFile(project, file);
  }


  @Override
  @Nullable
  public GradleBuildModel getProjectBuildModel() {
    return myProjectBuildFile == null ? null : new GradleBuildModelImpl(myProjectBuildFile);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull Module module) {
    VirtualFile file = myBuildModelContext.getGradleBuildFile(module);
    return file == null ? null : getModuleBuildModel(file);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull File modulePath) {
    VirtualFile file = myBuildModelContext.getGradleBuildFile(modulePath);
    return file == null ? null : getModuleBuildModel(file);
  }

  /**
   * Gets the {@link GradleBuildModel} for the given {@link VirtualFile}. Please prefer using {@link #getModuleBuildModel(Module)} if
   * possible.
   *
   * @param file the file to parse, this file should be a Gradle build file that represents a Gradle Project (Idea Module or Project). The
   *             given file must also belong to the {@link Project} for which this {@link ProjectBuildModel} was created.
   * @return the build model for the requested file
   */
  @Override
  @NotNull
  public GradleBuildModel getModuleBuildModel(@NotNull VirtualFile file) {
    GradleBuildFile dslFile = myBuildModelContext.getOrCreateBuildFile(file, false);
    return new GradleBuildModelImpl(dslFile);
  }

  @Override
  @Nullable
  public GradleSettingsModel getProjectSettingsModel() {
    VirtualFile virtualFile = getProjectSettingsFile();
    if (virtualFile == null) return null;

    GradleSettingsFile settingsFile = myBuildModelContext.getOrCreateSettingsFile(virtualFile);
    return new GradleSettingsModelImpl(settingsFile);
  }

  @Nullable
  private VirtualFile getProjectSettingsFile() {
    VirtualFile virtualFile;
    // If we don't have a root build file, guess the location of the settings file from the project.
    if (myProjectBuildFile == null) {
      virtualFile = myBuildModelContext.getProjectSettingsFile();
    } else {
      virtualFile = myProjectBuildFile.tryToFindSettingsFile();
    }

    if (virtualFile == null) {
      return null;
    }
    return virtualFile;
  }

  @Override
  public void applyChanges() {
    runOverProjectTree(file -> {
      file.applyChanges();
      file.saveAllChanges();
    });

  }

  @Override
  public void resetState() {
    runOverProjectTree(GradleDslFile::resetState);
  }

  @Override
  public void reparse() {
    // myBuildModelContext has all the files removed when reset() is called. We need to ensure we collect the files before calling reset.
    List<GradleDslFile> files = myBuildModelContext.getAllRequestedFiles();
    myBuildModelContext.reset();
    files.forEach(GradleDslFile::reparse);
  }

  @NotNull
  @Override
  public List<GradleBuildModel> getAllIncludedBuildModels() {
    List<GradleBuildModel> allModels = new ArrayList<>();
    if (myProjectBuildFile != null) {
      allModels.add(new GradleBuildModelImpl(myProjectBuildFile));
    }

    GradleSettingsModel settingsModel = getProjectSettingsModel();
    if (settingsModel == null) {
      return allModels;
    }

    allModels.addAll(settingsModel.modulePaths().stream().map((modulePath) -> {
      // This should have already been added above
      if (modulePath.equals(":")) {
        return null;
      }

      File moduleDir = settingsModel.moduleDirectory(modulePath);
      if (moduleDir == null) {
        return null;
      }

      VirtualFile file = myBuildModelContext.getGradleBuildFile(moduleDir);
      if (file == null) {
        return null;
      }

      return getModuleBuildModel(file);
    }).filter(Objects::nonNull).collect(Collectors.toList()));
    return allModels;
  }

  private void runOverProjectTree(@NotNull Consumer<GradleDslFile> func) {
    myBuildModelContext.getAllRequestedFiles().forEach(func);
  }
}
