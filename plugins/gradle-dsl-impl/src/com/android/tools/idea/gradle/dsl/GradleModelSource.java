/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.dsl.GradleUtil.getBaseDirPath;

public final class GradleModelSource extends GradleModelProvider {

  private static final BuildModelContext.ResolvedConfigurationFileLocationProvider myResolvedConfigurationFileLocationProvider;

  static {
    myResolvedConfigurationFileLocationProvider = new ResolvedConfigurationFileLocationProviderImpl();
  }

  public static class GradleModelProviderFactoryImpl implements GradleModelProviderFactory {
    @Override
    public GradleModelProvider get() {
      return new GradleModelSource();
    }
  }

  @NotNull
  @Override
  public ProjectBuildModel getProjectModel(@NotNull Project project) {
    BuildModelContext context = createContext(project);
    VirtualFile file = context.getGradleBuildFile(getBaseDirPath(project));
    return new ProjectBuildModelImpl(project, file, context);
  }

  @Override
  @Nullable
  public ProjectBuildModel getProjectModel(@NotNull Project hostProject, @NotNull String compositeRoot) {
    BuildModelContext context = createContext(hostProject);
    VirtualFile file = context.getGradleBuildFile(new File(compositeRoot));
    if (file == null) {
      return null;
    }
    return new ProjectBuildModelImpl(hostProject, file, context);
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Project project) {
    BuildModelContext context = createContext(project);
    VirtualFile file = context.getGradleBuildFile(getBaseDirPath(project));
    return file != null ? internalCreateBuildModel(context, file, project.getName()) : null;
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Module module) {
    BuildModelContext context = createContext(module.getProject());
    VirtualFile file = context.getGradleBuildFile(module);
    return file != null ? internalCreateBuildModel(context, file, module.getName()) : null;
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return internalCreateBuildModel(createContext(project), file, "<Unknown>");
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    return internalCreateBuildModel(createContext(project), file, moduleName);
  }

  @Nullable
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull Project project) {
    BuildModelContext context = createContext(project);
    VirtualFile file = context.getGradleSettingsFile(getBaseDirPath(project));
    return file != null ? parseSettingsFile(context, file, project, "settings") : null;
  }

  @NotNull
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull VirtualFile settingsFile, @NotNull Project hostProject) {
    return parseSettingsFile(createContext(hostProject), settingsFile, hostProject, "settings");
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

  @Nullable
  @Override
  public ArtifactDependencySpec getArtifactDependencySpec(@NotNull String notation) {
    return ArtifactDependencySpecImpl.create(notation);
  }

  @NotNull
  private static GradleBuildModel internalCreateBuildModel(@NotNull BuildModelContext context,
                                                           @NotNull VirtualFile file,
                                                           @NotNull String moduleName) {
    return new GradleBuildModelImpl(context.getOrCreateBuildFile(file, moduleName, false));
  }

  /**
   * This method is left here to ensure that when needed we can construct a settings model with only the virtual file.
   * In most cases {@link GradleSettingsModel}s should be obtained from the {@link ProjectBuildModel}.
   */
  @NotNull
  private static GradleSettingsModel parseSettingsFile(@NotNull BuildModelContext context,
                                                       @NotNull VirtualFile file,
                                                       @NotNull Project project,
                                                       @NotNull String moduleName) {
    GradleSettingsFile settingsFile = new GradleSettingsFile(file, project, moduleName, context);
    settingsFile.parse();
    return new GradleSettingsModelImpl(settingsFile);
  }

  @NotNull
  private static BuildModelContext createContext(@NotNull Project project) {
    return BuildModelContext.create(project, myResolvedConfigurationFileLocationProvider);
  }
}
