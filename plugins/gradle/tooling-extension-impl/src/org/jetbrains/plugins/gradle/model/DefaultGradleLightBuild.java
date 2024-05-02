// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

@ApiStatus.Internal
public final class DefaultGradleLightBuild implements GradleLightBuild, Serializable {

  private final @NotNull String myName;
  private final @NotNull DefaultBuildIdentifier myBuildIdentifier;
  private final @NotNull Collection<DefaultGradleLightProject> myProjects = new ArrayList<>(0);

  private @Nullable DefaultBuildIdentifier myParentBuildIdentifier = null;

  private DefaultGradleLightBuild(@NotNull String name, @NotNull File rootDir) {
    myName = name;
    myBuildIdentifier = new DefaultBuildIdentifier(rootDir);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull BuildIdentifier getBuildIdentifier() {
    return myBuildIdentifier;
  }

  @Override
  public @NotNull Collection<? extends GradleLightProject> getProjects() {
    return myProjects;
  }

  @Override
  public @Nullable BuildIdentifier getParentBuildIdentifier() {
    return myParentBuildIdentifier;
  }

  public void setParentBuildIdentifier(@Nullable DefaultBuildIdentifier parentBuildIdentifier) {
    myParentBuildIdentifier = parentBuildIdentifier;
  }

  public static @NotNull DefaultGradleLightBuild convertGradleBuild(@NotNull GradleBuild gradleBuild) {
    String name = gradleBuild.getRootProject().getName();
    File rootDir = gradleBuild.getBuildIdentifier().getRootDir();
    DefaultGradleLightBuild build = new DefaultGradleLightBuild(name, rootDir);
    for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
      build.myProjects.add(DefaultGradleLightProject.convertGradleProject(gradleProject));
    }
    return build;
  }
}
