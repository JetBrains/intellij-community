// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultGradleLightBuild;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
public class GradleModelHolderState implements Serializable {

  private final @Nullable DefaultGradleLightBuild myRootBuild;
  private final @NotNull Collection<DefaultGradleLightBuild> myNestedBuilds;
  private final @Nullable BuildEnvironment myBuildEnvironment;
  private final @NotNull Map<GradleModelId, Object> myModels;

  private final @Nullable GradleModelFetchPhase myPhase;

  public GradleModelHolderState(
    @Nullable DefaultGradleLightBuild rootBuild,
    @NotNull Collection<DefaultGradleLightBuild> nestedBuilds,
    @Nullable BuildEnvironment buildEnvironment,
    @NotNull Map<GradleModelId, Object> models
  ) {
    this(rootBuild, nestedBuilds, buildEnvironment, models, null);
  }

  public GradleModelHolderState(
    @Nullable DefaultGradleLightBuild rootBuild,
    @NotNull Collection<DefaultGradleLightBuild> nestedBuilds,
    @Nullable BuildEnvironment buildEnvironment,
    @NotNull Map<GradleModelId, Object> models,
    @Nullable GradleModelFetchPhase phase
  ) {
    myPhase = phase;
    myRootBuild = rootBuild;
    myNestedBuilds = nestedBuilds;
    myBuildEnvironment = buildEnvironment;
    myModels = models;
  }

  public @Nullable DefaultGradleLightBuild getRootBuild() {
    return myRootBuild;
  }

  public @NotNull Collection<DefaultGradleLightBuild> getNestedBuilds() {
    return myNestedBuilds;
  }

  public @Nullable BuildEnvironment getBuildEnvironment() {
    return myBuildEnvironment;
  }

  public @NotNull Map<GradleModelId, Object> getModels() {
    return myModels;
  }

  public @Nullable GradleModelFetchPhase getPhase() {
    return myPhase;
  }

  @Contract(pure = true)
  public @NotNull GradleModelHolderState current() {
    return new GradleModelHolderState(myRootBuild, myNestedBuilds, myBuildEnvironment, myModels, myPhase);
  }

  @Contract(pure = true)
  public @NotNull GradleModelHolderState withPhase(@NotNull GradleModelFetchPhase phase) {
    return new GradleModelHolderState(myRootBuild, myNestedBuilds, myBuildEnvironment, myModels, phase);
  }
}
