// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

@ApiStatus.Internal
class DefaultProject implements Project, Serializable {

  private final String myName;
  private final DefaultProjectIdentifier myProjectIdentifier;

  private DefaultProject(@NotNull String name, @NotNull File rootDir, @NotNull String projectPath) {
    myName = name;
    myProjectIdentifier = new DefaultProjectIdentifier(rootDir, projectPath);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public ProjectIdentifier getProjectIdentifier() {
    return myProjectIdentifier;
  }

  @Override
  public String toString() {
    return "ProjectModel{" +
           "name='" + myName + '\'' +
           ", id=" + myProjectIdentifier +
           '}';
  }

  public static @NotNull Project convertGradleProject(@NotNull BasicGradleProject gradleProject) {
    String name = gradleProject.getName();
    ProjectIdentifier projectIdentifier = gradleProject.getProjectIdentifier();
    File rootDir = projectIdentifier.getBuildIdentifier().getRootDir();
    String projectPath = projectIdentifier.getProjectPath();
    return new DefaultProject(name, rootDir, projectPath);
  }

  public static @NotNull Project convertIdeaProject(@NotNull IdeaModule ideaModule) {
    GradleProject gradleProject = ideaModule.getGradleProject();
    String name = gradleProject.getName();
    ProjectIdentifier projectIdentifier = gradleProject.getProjectIdentifier();
    File rootDir = projectIdentifier.getBuildIdentifier().getRootDir();
    String projectPath = projectIdentifier.getProjectPath();
    return new DefaultProject(name, rootDir, projectPath);
  }
}
