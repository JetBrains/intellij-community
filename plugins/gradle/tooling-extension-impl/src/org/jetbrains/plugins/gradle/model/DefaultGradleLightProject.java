// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil.isBuildTreePathAvailable;

@ApiStatus.Internal
public final class DefaultGradleLightProject implements GradleLightProject, Serializable {

  private final @NotNull DefaultGradleLightBuild myBuildModel;

  private final @NotNull String myName;
  private final @NotNull String myPath;
  private final @Nullable String myIdentityPath;
  private final @NotNull File myProjectDirectory;
  private final @NotNull DefaultProjectIdentifier myProjectIdentifier;
  private final @NotNull List<DefaultGradleLightProject> myChildren = new ArrayList<>();

  public DefaultGradleLightProject(
    @NotNull DefaultGradleLightBuild buildModel,
    @NotNull BasicGradleProject gradleProject,
    @NotNull GradleVersion gradleVersion
  ) {
    myBuildModel = buildModel;
    myName = gradleProject.getName();
    myPath = gradleProject.getPath();
    myIdentityPath = isBuildTreePathAvailable(gradleVersion) ? gradleProject.getBuildTreePath() : null;
    myProjectDirectory = gradleProject.getProjectDirectory();
    myProjectIdentifier = new DefaultProjectIdentifier(
      gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir(),
      gradleProject.getProjectIdentifier().getProjectPath()
    );
  }

  @Override
  public @NotNull GradleLightBuild getBuild() {
    return myBuildModel;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getPath() {
    return myPath;
  }

  @Override
  public @NotNull String getIdentityPath() {
    if (myIdentityPath != null) {
      return myIdentityPath;
    }
    return getProjectIdentityPath(this);
  }

  /**
   * Calculates the identity path - same value as {@link BasicGradleProject#getBuildTreePath()} since 8.2.
   * TODO remove this method when IDEA stops supporting Gradle versions older than 8.2.
   */
  @VisibleForTesting
  public static @NotNull String getProjectIdentityPath(@NotNull GradleLightProject project) {
    String buildIdentityPath = getBuildIdentityPath(project.getBuild());
    if (buildIdentityPath.equals(":")) {
      return project.getPath();
    }
    if (project.getPath().equals(":")) {
      return buildIdentityPath;
    }
    return buildIdentityPath + project.getPath();
  }

  private static String getBuildIdentityPath(@NotNull GradleLightBuild build) {
    GradleLightBuild parentBuild = build.getParentBuild();
    if (parentBuild == null) {
      if (build.getName().equals("buildSrc")) return ":buildSrc";
      return ":";
    }
    String parentIdentityPath = getBuildIdentityPath(parentBuild);
    if (parentIdentityPath.equals(":")) {
      return ":" + build.getName();
    }
    return parentIdentityPath + ":" + build.getName();
  }

  @Override
  public @NotNull File getProjectDirectory() {
    return myProjectDirectory;
  }

  @Override
  public @NotNull ProjectIdentifier getProjectIdentifier() {
    return myProjectIdentifier;
  }

  @Override
  public @NotNull List<DefaultGradleLightProject> getChildProjects() {
    return myChildren;
  }

  public void addChildProject(@NotNull DefaultGradleLightProject childProject) {
    myChildren.add(childProject);
  }

  @Override
  public String toString() {
    return "ProjectModel{" +
           "name='" + myName + '\'' +
           ", id=" + myProjectIdentifier +
           '}';
  }
}
