// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * {@link GradleLightBuild} is a "lightweight" model represents a {@link org.gradle.tooling.model.gradle.GradleBuild}.
 * It can be used to access "build-level" models for the related Gradle build.
 *
 * @see GradleLightProject
 * @see org.gradle.tooling.model.gradle.GradleBuild
 *
 * @author Vladislav.Soroka
 */
public interface GradleLightBuild extends BuildModel {

  @NotNull
  String getName();

  @Override
  @NotNull
  BuildIdentifier getBuildIdentifier();

  @Nullable
  GradleLightBuild getParentBuild();

  @NotNull
  GradleLightProject getRootProject();

  @NotNull
  Collection<? extends GradleLightProject> getProjects();
}
