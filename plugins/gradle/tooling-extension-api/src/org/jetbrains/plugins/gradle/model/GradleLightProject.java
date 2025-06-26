// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.ProjectModel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * {@link GradleLightProject} is a "lightweight" model represents a {@link org.gradle.tooling.model.GradleProject}.
 * It can be used to access "project-level" models for the related Gradle project.
 *
 * @see GradleLightBuild
 * @see org.gradle.tooling.model.GradleProject
 * @see org.gradle.tooling.model.gradle.BasicGradleProject
 *
 * @author Vladislav.Soroka
 */
public interface GradleLightProject extends ProjectModel {

  @NotNull
  GradleLightBuild getBuild();

  @NotNull
  String getName();

  @NotNull
  String getPath();

  @NotNull
  String getIdentityPath();

  @NotNull
  File getProjectDirectory();

  @Override
  @NotNull
  ProjectIdentifier getProjectIdentifier();

  @NotNull
  Collection<? extends GradleLightProject> getChildProjects();
}
