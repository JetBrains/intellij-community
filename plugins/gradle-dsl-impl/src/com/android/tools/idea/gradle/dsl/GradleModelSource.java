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
package com.android.tools.idea.gradle.dsl;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleModelSource extends GradleModelProvider {

  public static class GradleModelProviderFactoryImpl implements GradleModelProviderFactory {
    @Override
    public GradleModelProvider get() {
      return new GradleModelSource();
    }
  }

  @NotNull
  @Override
  public ProjectBuildModel getProjectModel(@NotNull Project project) {
    return ProjectBuildModelImpl.get(project);
  }

  @Override
  @Nullable
  public ProjectBuildModel getProjectModel(@NotNull Project hostProject, @NotNull String compositeRoot) {
    return ProjectBuildModelImpl.get(hostProject, compositeRoot);
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Project project) {
    return GradleBuildModelImpl.get(project);
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Module module) {
    return GradleBuildModelImpl.get(module);
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return GradleBuildModelImpl.parseBuildFile(file, project);
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    return GradleBuildModelImpl.parseBuildFile(file, project, moduleName);
  }

  @Nullable
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull Project project) {
    return GradleSettingsModelImpl.get(project);
  }

  @NotNull
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull VirtualFile settingsFile, @NotNull Project hostProject) {
    return GradleSettingsModelImpl.get(settingsFile, hostProject);
  }

  @NotNull
  @Override
  public ArtifactDependencySpec getArtifactDependencySpec(@NotNull String name, @Nullable String group, @Nullable String version) {
    return new ArtifactDependencySpecImpl(name, group, version);
  }

  @NotNull
  @Override
  public ArtifactDependencySpec getArtifactDependencySpec(@NotNull String name,
                                                          @Nullable String group,
                                                          @Nullable String version,
                                                          @Nullable String classifier,
                                                          @Nullable String extension) {
    return new ArtifactDependencySpecImpl(name, group, version, classifier, extension);
  }

  @NotNull
  @Override
  public ArtifactDependencySpec getArtifactDependencySpec(@NotNull ArtifactDependencyModel dependency) {
    return ArtifactDependencySpecImpl.create(dependency);
  }

  @Nullable
  @Override
  public ArtifactDependencySpec getArtifactDependencySpec(@NotNull String notation) {
    return ArtifactDependencySpecImpl.create(notation);
  }
}
