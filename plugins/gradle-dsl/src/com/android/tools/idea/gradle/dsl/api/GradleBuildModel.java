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

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

public interface GradleBuildModel extends GradleFileModel {
  /**
   * Runs the given supplier and returns the result if no exception was thrown. If an exception was thrown then
   * log it to back intellijs logs and the AndroidStudioCrashReporter and return null.
   *
   * @param supplier supplier to run
   * @return supplied value or null if an exception was thrown
   */
  @Nullable
  static <T> T tryOrLog(@NotNull Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Exception e) {
      if (e instanceof ControlFlowException) {
        // Control-Flow exceptions should not be logged and reported.
        return null;
      }
      Logger logger = Logger.getInstance(ProjectBuildModel.class);
      logger.error(e);

      BuildModelErrorReporter.getInstance().report(e);
      return null;
    }
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} for the given projects root build.gradle file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   * In most cases if you want to use this method you should use {@link ProjectBuildModel} instead since it prevents files from being
   * parsed more than once and ensures changes in applied files are mirrored by any model obtained from the it.
   * @deprecated Use {@link ProjectBuildModel#get(Project)} instead.
   */
  @Deprecated
  @Nullable
  static GradleBuildModel get(@NotNull Project project) {
    return tryOrLog(() -> GradleModelProvider.get().getBuildModel(project));
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} for the given modules build.gradle file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   * @deprecated Use {@link ProjectBuildModel#get(Project)} instead.
   */
  @Deprecated
  @Nullable
  static GradleBuildModel get(@NotNull Module module) {
    return tryOrLog(() -> GradleModelProvider.get().getBuildModel(module));
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} by parsing the given file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @NotNull
  static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return GradleModelProvider.get().parseBuildFile(file, project);
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} by parsing the given file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @NotNull
  static GradleBuildModel parseBuildFile(@NotNull VirtualFile file,
                                         @NotNull Project project,
                                         @NotNull String moduleName) {
    return GradleModelProvider.get().parseBuildFile(file, project, moduleName);
  }

  /**
   * DO NOT USE. Use {#plugins()} instead. This method is required to keep plugin compatibility with the android plugin 3.1 and below.
   * This method may be removed in the future.
   */
  @Deprecated
  @NotNull
  List<GradleNotNullValue<String>> appliedPlugins();

  @NotNull
  List<PluginModel> plugins();

  @NotNull
  PluginModel applyPlugin(@NotNull String plugin);

  void removePlugin(@NotNull String plugin);

  @NotNull
  AndroidModel android();

  @NotNull
  BuildScriptModel buildscript();

  @NotNull
  ConfigurationsModel configurations();

  @NotNull
  DependenciesModel dependencies();

  @NotNull
  ExtModel ext();

  @NotNull
  JavaModel java();

  @NotNull
  RepositoriesModel repositories();

  /**
   * @return the models for files that are used by this GradleBuildModel.
   */
  @NotNull
  Set<GradleFileModel> getInvolvedFiles();

  /**
   * Removes repository property.
   */
  @TestOnly
  void removeRepositoriesBlocks();
}
