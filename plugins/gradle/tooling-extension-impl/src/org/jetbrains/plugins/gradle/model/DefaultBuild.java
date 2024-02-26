// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

@ApiStatus.Internal
public final class DefaultBuild implements Build, Serializable {

  private final String myName;
  private final DefaultBuildIdentifier myBuildIdentifier;
  private final Collection<Project> myProjects = new ArrayList<>(0);

  private DefaultBuildIdentifier myParentBuildIdentifier = null;

  private DefaultBuild(String name, File rootDir) {
    myName = name;
    myBuildIdentifier = new DefaultBuildIdentifier(rootDir);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public BuildIdentifier getBuildIdentifier() {
    return myBuildIdentifier;
  }

  @Override
  public Collection<Project> getProjects() {
    return myProjects;
  }

  @Override
  public BuildIdentifier getParentBuildIdentifier() {
    return myParentBuildIdentifier;
  }

  public void setParentBuildIdentifier(DefaultBuildIdentifier parentBuildIdentifier) {
    myParentBuildIdentifier = parentBuildIdentifier;
  }

  public static @NotNull Build convertGradleBuild(@NotNull GradleBuild gradleBuild) {
    String name = gradleBuild.getRootProject().getName();
    File rootDir = gradleBuild.getBuildIdentifier().getRootDir();
    DefaultBuild build = new DefaultBuild(name, rootDir);
    for (BasicGradleProject gradleProject : gradleBuild.getProjects()) {
      build.myProjects.add(DefaultProject.convertGradleProject(gradleProject));
    }
    return build;
  }
}
