// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.openapi.util.io.FileUtilRt;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

@ApiStatus.Internal
public final class GradleModelId implements Serializable {

  private final @NotNull String classId;
  private final @NotNull String buildId;
  private final @NotNull String projectId;

  public GradleModelId(@NotNull String classId, @NotNull String buildId, @NotNull String projectId) {
    this.classId = classId;
    this.buildId = buildId;
    this.projectId = projectId;
  }

  public boolean isForClass(@NotNull Class<?> modelClass) {
    return classId.equals(createClassId(modelClass));
  }

  public @NotNull String getClassId() {
    return classId;
  }

  public @NotNull String getBuildId() {
    return buildId;
  }

  public @NotNull String getProjectId() {
    return projectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GradleModelId)) return false;
    GradleModelId id = (GradleModelId)o;
    return Objects.equals(classId, id.classId) &&
           Objects.equals(buildId, id.buildId) &&
           Objects.equals(projectId, id.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classId, buildId, projectId);
  }

  @Override
  public String toString() {
    return classId + "/" + buildId + "/" + projectId;
  }

  public static @NotNull GradleModelId createBuildModelId(@NotNull BuildModel buildModel, @NotNull Class<?> modelClass) {
    BuildIdentifier buildIdentifier = buildModel.getBuildIdentifier();
    String classId = createClassId(modelClass);
    String buildId = createBuildId(buildIdentifier);
    String projectId = ":";
    return new GradleModelId(classId, buildId, projectId);
  }

  public static @NotNull GradleModelId createProjectModelId(@NotNull ProjectModel projectModel, @NotNull Class<?> modelClass) {
    ProjectIdentifier projectIdentifier = getProjectIdentifier(projectModel);
    BuildIdentifier buildIdentifier = projectIdentifier.getBuildIdentifier();
    String classId = createClassId(modelClass);
    String buildId = createBuildId(buildIdentifier);
    String projectId = projectIdentifier.getProjectPath();
    return new GradleModelId(classId, buildId, projectId);
  }

  private static @NotNull String createClassId(@NotNull Class<?> modelClass) {
    return modelClass.getSimpleName() + modelClass.getName().hashCode();
  }

  public static @NotNull String createBuildId(@NotNull BuildIdentifier buildIdentifier) {
    String path = buildIdentifier.getRootDir().getPath();
    String systemIndependentName = FileUtilRt.toSystemIndependentName(path);
    return String.valueOf(systemIndependentName.hashCode());
  }

  private static @NotNull ProjectIdentifier getProjectIdentifier(@NotNull ProjectModel project) {
    ProjectIdentifier projectIdentifier;
    if (project instanceof IdeaModule) {
      // do not use IdeaModule#getProjectIdentifier, it returns project identifier of the root project
      projectIdentifier = ((IdeaModule)project).getGradleProject().getProjectIdentifier();
    }
    else {
      projectIdentifier = project.getProjectIdentifier();
    }
    return projectIdentifier;
  }
}
