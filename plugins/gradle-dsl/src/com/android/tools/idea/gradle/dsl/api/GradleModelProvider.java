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

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GradleModelProvider {

  /**
   * The purpose if this class is to simplify moving DSL out of android and avoid depencency dsl-api on dsl-impl
   */
  @ApiStatus.Internal
  public interface GradleModelProviderFactory {
    GradleModelProvider get();
  }

  @NotNull
  public static GradleModelProvider get() {
    return ApplicationManager.getApplication().getService(GradleModelProviderFactory.class).get();
  }

  @NotNull
  public abstract ProjectBuildModel getProjectModel(@NotNull Project project);

  @Nullable
  public abstract ProjectBuildModel getProjectModel(@NotNull Project hostProject, @NotNull String compositeRoot);

  @Nullable
  public abstract GradleBuildModel getBuildModel(@NotNull Project project);

  @Nullable
  public abstract GradleBuildModel getBuildModel(@NotNull Module module);

  @NotNull
  public abstract GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project);

  @NotNull
  public abstract GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName);

  @Nullable
  public abstract GradleSettingsModel getSettingsModel(@NotNull Project project);

  @NotNull
  public abstract GradleSettingsModel getSettingsModel(@NotNull VirtualFile settingsFile, @NotNull Project hostProject);

  @NotNull
  public abstract ArtifactDependencySpec getArtifactDependencySpec(@NotNull String name,
                                                                   @Nullable String group,
                                                                   @Nullable String version);

  @NotNull
  public abstract ArtifactDependencySpec getArtifactDependencySpec(@NotNull String name,
                                                                   @Nullable String group,
                                                                   @Nullable String version,
                                                                   @Nullable String classifier,
                                                                   @Nullable String extension);

  @NotNull
  public abstract ArtifactDependencySpec getArtifactDependencySpec(@NotNull ArtifactDependencyModel dependency);

  @Nullable
  public abstract ArtifactDependencySpec getArtifactDependencySpec(@NotNull String notation);

}
