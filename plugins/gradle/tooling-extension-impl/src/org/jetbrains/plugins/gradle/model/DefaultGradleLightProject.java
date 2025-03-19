// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class DefaultGradleLightProject implements GradleLightProject, Serializable {

  private final @NotNull String myName;
  private final @NotNull String myPath;
  private final @NotNull File myProjectDirectory;
  private final @NotNull DefaultProjectIdentifier myProjectIdentifier;
  private final @NotNull List<DefaultGradleLightProject> myChildren = new ArrayList<>();

  public DefaultGradleLightProject(
    @NotNull String name,
    @NotNull String path,
    @NotNull File projectDirectory,
    @NotNull DefaultProjectIdentifier projectIdentifier
  ) {
    myName = name;
    myPath = path;
    myProjectDirectory = projectDirectory;
    myProjectIdentifier = projectIdentifier;
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
