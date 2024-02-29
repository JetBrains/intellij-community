// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction;

import org.gradle.tooling.model.build.BuildEnvironment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultBuild;

import java.io.Serializable;
import java.util.*;

@ApiStatus.Internal
public class GradleModelHolderState implements Serializable {

  private final @Nullable DefaultBuild myRootBuild;
  private final @NotNull Collection<DefaultBuild> myNestedBuilds;
  private final @Nullable BuildEnvironment myBuildEnvironment;
  private final @NotNull Map<GradleModelId, Object> myModels;

  private final byte[] myOpenTelemetryTraces;

  public GradleModelHolderState(
    @Nullable DefaultBuild rootBuild,
    @NotNull Collection<DefaultBuild> nestedBuilds,
    @Nullable BuildEnvironment buildEnvironment,
    @NotNull Map<GradleModelId, Object> models
  ) {
    this(rootBuild, nestedBuilds, buildEnvironment, models, new byte[0]);
  }

  public GradleModelHolderState(
    @Nullable DefaultBuild rootBuild,
    @NotNull Collection<DefaultBuild> nestedBuilds,
    @Nullable BuildEnvironment buildEnvironment,
    @NotNull Map<GradleModelId, Object> models,
    byte[] openTelemetryTraces
  ) {
    myRootBuild = rootBuild;
    myNestedBuilds = nestedBuilds;
    myBuildEnvironment = buildEnvironment;
    myModels = models;
    myOpenTelemetryTraces = openTelemetryTraces;
  }

  public @Nullable DefaultBuild getRootBuild() {
    return myRootBuild;
  }

  public @NotNull Collection<DefaultBuild> getNestedBuilds() {
    return myNestedBuilds;
  }

  public @Nullable BuildEnvironment getBuildEnvironment() {
    return myBuildEnvironment;
  }

  public @NotNull Map<GradleModelId, Object> getModels() {
    return myModels;
  }

  public byte[] getOpenTelemetryTraces() {
    return myOpenTelemetryTraces;
  }

  @Contract(pure = true)
  public @NotNull GradleModelHolderState withOpenTelemetryTraces(byte[] traces) {
    return new GradleModelHolderState(myRootBuild, myNestedBuilds, myBuildEnvironment, myModels, traces);
  }
}
